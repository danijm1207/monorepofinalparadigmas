"""
docker_controller.py - Control y orquestación de Docker Compose para backends evaluados.
Gestiona el ciclo de vida de los contenedores por perfil (Java, Scala, Kotlin),
mide tiempos de arranque y tamaños de imagen.
"""

from pathlib import Path
import subprocess
import time
from typing import Dict, Optional, Tuple
import httpx


class DockerController:
    """Controlador de Docker Compose para ejecución de perfiles aislados."""

    def __init__(self, workspace_root: Path):
        self.workspace_root = workspace_root

    def run_compose_command(
        self, args: list[str], timeout: int = 600
    ) -> Tuple[int, str, str]:
        """Ejecuta un comando docker compose en el directorio raíz."""
        cmd = ["docker", "compose"] + args
        res = subprocess.run(
            cmd,
            cwd=self.workspace_root,
            capture_output=True,
            text=True,
            timeout=timeout,
        )
        return res.returncode, res.stdout, res.stderr

    def down_clean(self, timeout: int = 120):
        """Detiene y destruye todos los contenedores y volúmenes: docker compose down -v."""
        self.run_compose_command(["down", "-v", "--remove-orphans"], timeout=timeout)

    def get_container_name(self, profile: str) -> str:
        return f"investigacion_backend_{profile}"

    def get_port(self, profile: str) -> int:
        ports = {"java": 8081, "scala": 8082, "kotlin": 8083}
        return ports.get(profile, 8080)

    def start_profile(
        self,
        profile: str,
        build: bool = True,
        health_timeout: float = 180.0,
        health_poll_interval: float = 0.5,
    ) -> Dict[str, any]:
        """
        Fase 1: docker compose down -v y up -d [--build].
        Mide el tiempo de arranque desde el inicio del comando hasta el primer 200 OK en /api/health.
        """
        self.down_clean()

        port = self.get_port(profile)
        health_url = f"http://localhost:{port}/api/health"

        args = ["--profile", profile, "up", "-d"]
        if build:
            args.append("--build")

        t_start = time.perf_counter()
        code, stdout, stderr = self.run_compose_command(args, timeout=300)
        if code != 0:
            raise RuntimeError(
                f"Error al iniciar perfil {profile} en Docker: {stderr}\n{stdout}"
            )

        # Esperar hasta que /api/health responda 200
        healthy = False
        t_healthy = 0.0
        while (time.perf_counter() - t_start) < health_timeout:
            try:
                with httpx.Client(timeout=2.0) as client:
                    resp = client.get(health_url)
                    if resp.status_code == 200:
                        healthy = True
                        t_healthy = time.perf_counter()
                        break
            except Exception:
                pass
            time.sleep(health_poll_interval)

        if not healthy:
            self.down_clean()
            raise TimeoutError(
                f"Timeout esperando respuesta saludable de {health_url} tras {health_timeout}s"
            )

        startup_time_seconds = t_healthy - t_start
        image_size_bytes = self.get_image_size(profile)

        return {
            "profile": profile,
            "port": port,
            "health_url": health_url,
            "startup_seconds": startup_time_seconds,
            "image_size_bytes": image_size_bytes,
        }

    def get_image_size(self, profile: str) -> int:
        """Obtiene el tamaño en bytes de la imagen del backend."""
        image_name = f"monorepofinalparadigmas-backend-{profile}"
        try:
            res = subprocess.run(
                ["docker", "image", "inspect", image_name, "--format", "{{.Size}}"],
                capture_output=True,
                text=True,
                timeout=10,
            )
            if res.returncode == 0 and res.stdout.strip():
                return int(res.stdout.strip())
        except Exception:
            pass
        return 0
