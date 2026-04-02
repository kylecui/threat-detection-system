"""
CSV adapter for batch processing of observables.
"""

import csv
import asyncio
from pathlib import Path
from typing import List, Dict, Any, Optional
from models import Observable
from app.service import ThreatIntelService


class CSVAdapter:
    """Handles CSV input/output for batch analysis."""

    REQUIRED_COLUMNS = ["type", "value"]

    def __init__(self, service: ThreatIntelService):
        self.service = service

    async def process_batch(
        self,
        input_file: Path,
        output_file: Optional[Path] = None,
        refresh: bool = False,
    ) -> List[Dict[str, Any]]:
        """
        Process a CSV file with observables.

        Args:
            input_file: Path to input CSV
            output_file: Optional path to write results
            refresh: Whether to skip cache

        Returns:
            List of analysis results
        """
        observables = self._read_csv(input_file)
        results = []

        for obs in observables:
            try:
                if obs.type == "ip":
                    verdict = await self.service.analyze_ip(obs.value, refresh=refresh)
                elif obs.type == "domain":
                    verdict = await self.service.analyze_domain(
                        obs.value, refresh=refresh
                    )
                elif obs.type == "url":
                    verdict = await self.service.analyze_url(obs.value, refresh=refresh)
                else:
                    verdict = None

                result = {
                    "type": obs.type,
                    "value": obs.value,
                    "success": verdict is not None,
                    "error": None if verdict else f"Unsupported type: {obs.type}",
                    "verdict": verdict.dict() if verdict else None,
                }
            except Exception as e:
                result = {
                    "type": obs.type,
                    "value": obs.value,
                    "success": False,
                    "error": str(e),
                    "verdict": None,
                }

            results.append(result)

        if output_file:
            self._write_csv(results, output_file)

        return results

    def _read_csv(self, file_path: Path) -> List[Observable]:
        """Read observables from CSV file."""
        observables = []

        with open(file_path, "r", encoding="utf-8") as f:
            reader = csv.DictReader(f)

            # Check required columns
            if not all(col in reader.fieldnames for col in self.REQUIRED_COLUMNS):
                raise ValueError(f"CSV must contain columns: {self.REQUIRED_COLUMNS}")

            for row in reader:
                obs_type = row.get("type", "").strip().lower()
                value = row.get("value", "").strip()

                if obs_type and value:
                    observables.append(Observable(type=obs_type, value=value))

        return observables

    def _write_csv(self, results: List[Dict[str, Any]], output_file: Path) -> None:
        """Write results to CSV file."""
        with open(output_file, "w", newline="", encoding="utf-8") as f:
            fieldnames = [
                "type",
                "value",
                "success",
                "error",
                "level",
                "score",
                "decision",
                "summary",
            ]
            writer = csv.DictWriter(f, fieldnames=fieldnames)
            writer.writeheader()

            for result in results:
                row = {
                    "type": result["type"],
                    "value": result["value"],
                    "success": result["success"],
                    "error": result.get("error") or "",
                    "level": "",
                    "score": "",
                    "decision": "",
                    "summary": "",
                }

                if result["verdict"]:
                    verdict = result["verdict"]
                    row.update(
                        {
                            "level": verdict.get("level", ""),
                            "score": verdict.get("final_score", ""),
                            "decision": verdict.get("decision", ""),
                            "summary": verdict.get("summary", ""),
                        }
                    )

                writer.writerow(row)
