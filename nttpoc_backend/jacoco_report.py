#!/usr/bin/env python3
"""
JaCoCo 커버리지 리포트 출력 스크립트
사용법:
  python3 jacoco_report.py           # XML 파싱만 (mvn test 별도 실행)
  python3 jacoco_report.py --run     # mvn test jacoco:report 실행 후 파싱
"""

import sys
import subprocess
import xml.etree.ElementTree as ET
from pathlib import Path

# ── 설정 ──────────────────────────────────────────────────────────────────────
JACOCO_XML   = Path(__file__).parent / "target/site/jacoco/jacoco.xml"
THRESHOLD    = 90.0   # 목표 커버리지 (%)
ROOT_PKG     = "com/mobigen/aiop/nttpoc/"

# ── ANSI 색상 ─────────────────────────────────────────────────────────────────
GREEN  = "\033[92m"
YELLOW = "\033[93m"
RED    = "\033[91m"
CYAN   = "\033[96m"
BOLD   = "\033[1m"
RESET  = "\033[0m"

def color(pct: float) -> str:
    if pct >= THRESHOLD:   return GREEN
    if pct >= 70.0:        return YELLOW
    return RED

def bar(pct: float, width: int = 20) -> str:
    filled = round(pct / 100 * width)
    return f"[{'█' * filled}{'░' * (width - filled)}]"

# ── Maven 실행 ────────────────────────────────────────────────────────────────
def run_maven():
    print(f"{CYAN}▶ mvn test jacoco:report 실행 중...{RESET}\n")
    result = subprocess.run(
        ["./mvnw", "test", "jacoco:report"],
        cwd=Path(__file__).parent,
        capture_output=False,
    )
    if result.returncode != 0:
        print(f"\n{RED}✘ Maven 빌드 실패 (exit code {result.returncode}){RESET}")
        sys.exit(1)
    print()

# ── XML 파싱 ──────────────────────────────────────────────────────────────────
def parse_report():
    if not JACOCO_XML.exists():
        print(f"{RED}✘ 리포트 파일을 찾을 수 없습니다: {JACOCO_XML}{RESET}")
        print(f"   먼저 'mvn test jacoco:report' 를 실행하거나 --run 옵션을 사용하세요.")
        sys.exit(1)

    tree = ET.parse(JACOCO_XML)
    root = tree.getroot()

    packages = []
    for pkg in root.findall("package"):
        name = pkg.get("name", "").replace(ROOT_PKG, "") or pkg.get("name", "")
        missed, covered = 0, 0
        for c in pkg.findall("counter"):
            if c.get("type") == "LINE":
                missed  += int(c.get("missed",  0))
                covered += int(c.get("covered", 0))
        total = missed + covered
        if total > 0:
            packages.append((name, covered, total))

    # 전체 합계
    t_missed, t_covered = 0, 0
    for c in root.findall("counter"):
        if c.get("type") == "LINE":
            t_missed  += int(c.get("missed",  0))
            t_covered += int(c.get("covered", 0))

    return packages, t_covered, t_missed + t_covered

# ── 출력 ──────────────────────────────────────────────────────────────────────
def print_report(packages, total_covered, total_lines):
    # 커버리지 낮은 순 정렬
    packages_sorted = sorted(packages, key=lambda x: x[1] / x[2])

    col_w = max(len(p[0]) for p in packages) + 2

    print(f"\n{BOLD}{'패키지':<{col_w}}  {'커버리지':>8}   {'라인':>12}   진행도{RESET}")
    print("─" * (col_w + 52))

    for name, covered, total in packages_sorted:
        pct = covered / total * 100
        c   = color(pct)
        print(
            f"{name:<{col_w}}  "
            f"{c}{pct:6.1f}%{RESET}   "
            f"{covered:>5}/{total:<5}   "
            f"{c}{bar(pct)}{RESET}"
        )

    # 구분선 + 합계
    print("─" * (col_w + 52))
    t_pct    = total_covered / total_lines * 100
    t_color  = color(t_pct)
    t_status = f"{GREEN}✔ PASS{RESET}" if t_pct >= THRESHOLD else f"{RED}✘ FAIL{RESET}"
    print(
        f"{'TOTAL':<{col_w}}  "
        f"{t_color}{BOLD}{t_pct:6.1f}%{RESET}   "
        f"{total_covered:>5}/{total_lines:<5}   "
        f"{t_color}{bar(t_pct)}{RESET}  "
        f"{t_status}  (목표 {THRESHOLD:.0f}%)"
    )
    print()

# ── 진입점 ────────────────────────────────────────────────────────────────────
if __name__ == "__main__":
    if "--run" in sys.argv:
        run_maven()

    packages, total_covered, total_lines = parse_report()
    print_report(packages, total_covered, total_lines)
