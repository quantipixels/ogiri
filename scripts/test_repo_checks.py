"""Base selection for scripts/repo-checks.py paths."""

import os
import shutil
import stat
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path


SCRIPT = Path(__file__).with_name("repo-checks.py")


def git(repo, *args):
    return subprocess.run(
        ["git", "-c", "user.name=Gate Test", "-c", "user.email=gate@example.com", *args],
        cwd=repo,
        check=True,
        capture_output=True,
        text=True,
    )


def sample_repo():
    directory = tempfile.TemporaryDirectory(prefix="ogiri-gate-")
    repo = Path(directory.name)
    git(repo, "init", "-b", "ori")
    docs = repo / "docs"
    docs.mkdir()
    (docs / "note.md").write_text("one\n")
    git(repo, "add", "docs/note.md")
    git(repo, "commit", "-m", "base")
    base = git(repo, "rev-parse", "HEAD").stdout.strip()
    (docs / "note.md").write_text("two\n")
    git(repo, "add", "docs/note.md")
    git(repo, "commit", "-m", "edit")
    return directory, repo, base


def run_paths(repo, base):
    output = repo / "github-output.txt"
    env = os.environ.copy()
    env["GATE_BASE_SHA"] = base
    env.pop("GATE_FORCE_FULL", None)
    env["GITHUB_OUTPUT"] = str(output)
    completed = subprocess.run(
        [sys.executable, str(SCRIPT), "paths"],
        cwd=repo,
        env=env,
        capture_output=True,
        text=True,
    )
    written = output.read_text() if output.exists() else ""
    return completed, written


class PathsBaseTest(unittest.TestCase):
    def test_reachable_base_uses_the_diff(self):
        directory, repo, base = sample_repo()
        self.addCleanup(directory.cleanup)
        completed, written = run_paths(repo, base)
        self.assertEqual(completed.returncode, 0, completed.stderr)
        self.assertEqual(written, "java=false\nparity=false\n")

    def test_missing_base_is_full_run(self):
        directory, repo, _base = sample_repo()
        self.addCleanup(directory.cleanup)
        missing = "b077ac8b2149a37b36bb3b66fd382543a2b5816d"
        completed, written = run_paths(repo, missing)
        self.assertEqual(completed.returncode, 0, completed.stderr)
        self.assertEqual(written, "java=true\nparity=true\n")

    def test_all_zero_base_is_full_run(self):
        directory, repo, _base = sample_repo()
        self.addCleanup(directory.cleanup)
        completed, written = run_paths(repo, "0" * 40)
        self.assertEqual(completed.returncode, 0, completed.stderr)
        self.assertEqual(written, "java=true\nparity=true\n")

    def test_reachable_base_diff_failure_still_fails(self):
        directory, repo, base = sample_repo()
        self.addCleanup(directory.cleanup)
        real = shutil.which("git")
        self.assertIsNotNone(real)
        bindir = Path(directory.name) / "bin"
        bindir.mkdir()
        wrapper = bindir / "git"
        wrapper.write_text(
            "#!/bin/sh\n"
            'if [ "$1" = "diff" ]; then\n'
            '  echo "induced diff failure" >&2\n'
            "  exit 128\n"
            "fi\n"
            f'exec "{real}" "$@"\n'
        )
        wrapper.chmod(wrapper.stat().st_mode | stat.S_IEXEC)
        output = repo / "github-output.txt"
        env = os.environ.copy()
        env["PATH"] = str(bindir) + os.pathsep + env.get("PATH", "")
        env["GATE_BASE_SHA"] = base
        env.pop("GATE_FORCE_FULL", None)
        env["GITHUB_OUTPUT"] = str(output)
        completed = subprocess.run(
            [sys.executable, str(SCRIPT), "paths"],
            cwd=repo,
            env=env,
            capture_output=True,
            text=True,
        )
        self.assertEqual(completed.returncode, 1, completed.stdout + completed.stderr)
        self.assertIn("gate failed", completed.stderr)


if __name__ == "__main__":
    unittest.main()
