"""
memory_sampler.py - Muestreo continuo de memoria con docker stats.
Ejecuta muestreo cada 500 ms en un hilo de fondo, calcula picos y medias por fase
y provee medición de memoria en reposo.
"""

import json
from pathlib import Path
import re
import subprocess
import threading
import time
from typing import Dict, List, Optional, Tuple


def parse_memory_bytes(mem_str: str) -> Optional[int]:
    """
    Convierte una cadena de uso de memoria como '125.4MiB / 2GiB' o '45.2MB' a bytes enteros.
    """
    if not mem_str:
        return None
    # Extraer la parte antes del '/' si existe
    usage_part = mem_str.split("/")[0].strip()

    match = re.match(r"^([\d\.]+)\s*([A-Za-z]+)$", usage_part)
    if not match:
        return None

    value = float(match.group(1))
    unit = match.group(2).lower()

    multipliers = {
        "b": 1,
        "kib": 1024,
        "kb": 1000,
        "mib": 1024**2,
        "mb": 1000**2,
        "gib": 1024**3,
        "gb": 1000**3,
    }

    mult = multipliers.get(unit, 1)
    return int(value * mult)


class MemorySampler:
    """Muestreador continuo de memoria para contenedores Docker."""

    def __init__(self, container_name: str, interval_seconds: float = 0.5):
        self.container_name = container_name
        self.interval_seconds = interval_seconds
        self.running = False
        self.thread: Optional[threading.Thread] = None
        self.current_phase: str = "init"
        self.samples: List[Dict[str, any]] = []
        self._lock = threading.Lock()

    def _sample_loop(self):
        while self.running:
            try:
                # docker stats --no-stream --format "{{.MemUsage}}" <container>
                res = subprocess.run(
                    [
                        "docker",
                        "stats",
                        "--no-stream",
                        "--format",
                        "{{.MemUsage}}",
                        self.container_name,
                    ],
                    capture_output=True,
                    text=True,
                    timeout=5,
                )
                if res.returncode == 0 and res.stdout.strip():
                    usage_str = res.stdout.strip()
                    mem_bytes = parse_memory_bytes(usage_str)
                    if mem_bytes is not None:
                        with self._lock:
                            self.samples.append(
                                {
                                    "timestamp": time.time(),
                                    "phase": self.current_phase,
                                    "bytes": mem_bytes,
                                    "raw": usage_str,
                                }
                            )
            except Exception:
                pass  # Si el contenedor se está deteniendo o hay timeout, ignorar

            time.sleep(self.interval_seconds)

    def start(self, initial_phase: str = "init"):
        """Inicia el hilo de muestreo."""
        self.current_phase = initial_phase
        self.running = True
        self.thread = threading.Thread(target=self._sample_loop, daemon=True)
        self.thread.start()

    def set_phase(self, phase_name: str):
        """Cambia la fase actual registrada para las muestras de memoria."""
        with self._lock:
            self.current_phase = phase_name

    def stop(self) -> List[Dict[str, any]]:
        """Detiene el hilo de muestreo y retorna todas las muestras recolectadas."""
        self.running = False
        if self.thread and self.thread.is_alive():
            self.thread.join(timeout=3)
        return list(self.samples)

    def get_phase_stats(self, phase_name: str) -> Dict[str, float]:
        """Calcula el pico y media de memoria en bytes para una fase dada."""
        with self._lock:
            phase_samples = [s["bytes"] for s in self.samples if s["phase"] == phase_name]

        if not phase_samples:
            return {"peak_bytes": 0, "mean_bytes": 0, "count": 0}

        return {
            "peak_bytes": max(phase_samples),
            "mean_bytes": sum(phase_samples) / len(phase_samples),
            "count": len(phase_samples),
        }

    def save_samples(self, target_path: Path):
        """Exporta las muestras a un archivo JSON."""
        target_path.parent.mkdir(parents=True, exist_ok=True)
        with self._lock:
            data = list(self.samples)
        with open(target_path, "w", encoding="utf-8") as f:
            json.dump(data, f, indent=2)


def measure_idle_memory(
    container_name: str,
    duration_seconds: float = 10.0,
    wait_before_seconds: float = 10.0,
    interval_seconds: float = 0.5,
) -> Dict[str, float]:
    """
    Fase 3: Medición de memoria en reposo.
    Espera wait_before_seconds tras health OK y luego promedia el consumo durante duration_seconds.
    """
    if wait_before_seconds > 0:
        time.sleep(wait_before_seconds)

    sampler = MemorySampler(container_name, interval_seconds=interval_seconds)
    sampler.start("idle")
    time.sleep(duration_seconds)
    sampler.stop()

    stats = sampler.get_phase_stats("idle")
    return stats
