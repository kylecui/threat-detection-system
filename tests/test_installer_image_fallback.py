import shutil
import subprocess
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
INSTALLER = ROOT / "install.sh"


def extract_function(source: str, name: str) -> str:
    marker = f"{name}() {{"
    start = source.index(marker)
    body, separator, _ = source[start:].partition("\n}\n")
    if not separator:
        raise AssertionError(f"Unable to extract {name}")
    return f"{body}\n}}\n"


class InstallerImageFallbackTests(unittest.TestCase):
    def test_flink_fallback_is_verified_retagged_and_completed_in_one_run(self) -> None:
        bash = shutil.which("bash")
        if not bash:
            git_bash = Path(r"C:\Program Files\Git\bin\bash.exe")
            bash = str(git_bash) if git_bash.is_file() else None
        if not bash:
            self.skipTest("bash is not available")

        source = INSTALLER.read_text(encoding="utf-8")
        constants = "\n".join(
            line
            for line in source.splitlines()
            if line.startswith(
                (
                    "FLINK_RUNTIME_IMAGE=",
                    "FLINK_FALLBACK_IMAGE=",
                    "FLINK_EXPECTED_DIGEST=",
                )
            )
        )
        functions = "\n".join(
            extract_function(source, name)
            for name in (
                "image_pull_fallback",
                "image_expected_digest",
                "image_matches_expected_digest",
                "pull_image_candidate",
                "tag_verified_fallback_image",
                "pull_container_image",
            )
        )

        harness = (
            "set -Eeuo pipefail\n"
            "DOCKER_PULL_RETRIES=1\n"
            "DOCKER_PULL_TIMEOUT_SECONDS=5\n"
            f"{constants}\n"
            "declare -A LOCAL_IMAGES=()\n"
            "declare -a PULL_CALLS=()\n"
            "info() { :; }\n"
            "ok() { printf 'OK:%s\\n' \"$*\"; }\n"
            "warn() { printf 'WARN:%s\\n' \"$*\"; }\n"
            "timeout() { shift 3; \"$@\"; }\n"
            "docker() {\n"
            "  if [[ \"$1 $2\" == \"image inspect\" ]]; then\n"
            "    local image\n"
            "    if [[ \"${3:-}\" == \"--format\" ]]; then image=\"$5\"; else image=\"$3\"; fi\n"
            "    [[ -n \"${LOCAL_IMAGES[$image]+present}\" ]] || return 1\n"
            "    if [[ \"${3:-}\" == \"--format\" ]]; then\n"
            "      printf '%s@%s\\n' \"$image\" \"${LOCAL_IMAGES[$image]}\"\n"
            "    fi\n"
            "    return 0\n"
            "  fi\n"
            "  if [[ \"$1\" == \"pull\" ]]; then\n"
            "    PULL_CALLS+=(\"$2\")\n"
            "    [[ \"$2\" == \"$FLINK_FALLBACK_IMAGE\" ]] || return 1\n"
            "    LOCAL_IMAGES[\"$2\"]=\"$FLINK_EXPECTED_DIGEST\"\n"
            "    return 0\n"
            "  fi\n"
            "  if [[ \"$1 $2\" == \"image tag\" ]]; then\n"
            "    [[ -n \"${LOCAL_IMAGES[$3]+present}\" ]] || return 1\n"
            "    LOCAL_IMAGES[\"$4\"]=\"${LOCAL_IMAGES[$3]}\"\n"
            "    return 0\n"
            "  fi\n"
            "  return 2\n"
            "}\n"
            f"{functions}\n"
            "pull_container_image \"$FLINK_RUNTIME_IMAGE\"\n"
            "[[ \"${LOCAL_IMAGES[$FLINK_RUNTIME_IMAGE]}\" == \"$FLINK_EXPECTED_DIGEST\" ]]\n"
            "[[ \"${PULL_CALLS[*]}\" == \"$FLINK_RUNTIME_IMAGE $FLINK_FALLBACK_IMAGE\" ]]\n"
            "printf 'calls=%s\\n' \"${PULL_CALLS[*]}\"\n"
        )

        result = subprocess.run(
            [bash, "-c", harness],
            cwd=ROOT,
            text=True,
            capture_output=True,
            timeout=30,
            check=False,
        )
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertIn("Verified fallback image ready as", result.stdout)
        self.assertIn(
            "calls=flink:1.18.1-scala_2.12-java17 "
            "docker.m.daocloud.io/library/flink:1.18.1-scala_2.12-java17",
            result.stdout,
        )


if __name__ == "__main__":
    unittest.main()
