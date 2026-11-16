"""
environment.py - Recolección de metadatos del entorno de hardware, software y commit de Git.
Asegura la trazabilidad y reproducibilidad de las mediciones registradas en environment.json.
"""

import json
from pathlib import Path
import platform
import subprocess
from typing import Any, Dict
import psutil


def get_git_commit(cwd: Path) -> str:
    """Obtiene el hash del commit actual de Git."""
    try:
        res = subprocess.run(
            ["git", "rev-parse", "HEAD"],
            cwd=cwd,
            capture_output=True,
            text=True,
            check=True,
        )
        return res.stdout.strip()
    except Exception:
        return "unknown"


def get_docker_version() -> str:
    """Obtiene la versión de Docker instalada."""
    try:
        res = subprocess.run(
            ["docker", "--version"],
            capture_output=True,
            text=True,
            check=True,
        )
        return res.stdout.strip()
    except Exception:
        return "not available"


def get_docker_compose_version() -> str:
    """Obtiene la versión de Docker Compose instalada."""
    try:
        res = subprocess.run(
            ["docker", "compose", "version"],
            capture_output=True,
            text=True,
            check=True,
        )
        return res.stdout.strip()
    except Exception:
        return "not available"


def get_jdk_version() -> str:
    """Obtiene la versión del JDK local si está configurado."""
    try:
        res = subprocess.run(
            ["java", "-version"],
            capture_output=True,
            text=True,
        )
        # java -version imprime en stderr
        output = res.stderr or res.stdout
        first_line = output.splitlines()[0] if output else "unknown"
        return first_line.strip()
    except Exception:
        return "not available"


def collect_environment_metadata(workspace_root: Path) -> Dict[str, Any]:
    """Recolecta información completa de CPU, RAM, OS, Docker y Git."""
    cpu_freq = psutil.cpu_freq()
    freq_mhz = cpu_freq.current if cpu_freq else 0

    virtual_mem = psutil.virtual_memory()

    return {
        "os": {
            "system": platform.system(),
            "release": platform.release(),
            "version": platform.version(),
            "machine": platform.machine(),
            "processor": platform.processor(),
        },
        "cpu": {
            "physical_cores": psutil.cpu_count(logical=False),
            "logical_cores": psutil.cpu_count(logical=True),
            "frequency_mhz": freq_mhz,
        },
        "memory": {
            "total_bytes": virtual_mem.total,
            "total_gb": round(virtual_mem.total / (1024**3), 2),
            "available_bytes": virtual_mem.available,
        },
        "tools": {
            "python_version": platform.python_version(),
            "docker_version": get_docker_version(),
            "docker_compose_version": get_docker_compose_version(),
            "jdk_version": get_jdk_version(),
        },
        "git": {
            "commit": get_git_commit(workspace_root),
        },
    }


def save_environment_metadata(workspace_root: Path, target_path: Path) -> None:
    """Guarda environment.json en la ruta especificada."""
    data = collect_environment_metadata(workspace_root)
    target_path.parent.mkdir(parents=True, exist_ok=True)
    with open(target_path, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2)
