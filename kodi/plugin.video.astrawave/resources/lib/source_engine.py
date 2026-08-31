from dataclasses import dataclass
from typing import Iterable, List, Protocol


@dataclass(frozen=True)
class MediaRequest:
    media_type: str
    title: str
    year: int | None = None
    tmdb_id: int | None = None
    season: int | None = None
    episode: int | None = None


@dataclass
class Source:
    provider: str
    url: str
    label: str = ""
    quality: int = 0
    bitrate_kbps: int = 0
    latency_ms: int = 9999
    uptime: float = 0.0
    direct: bool = True
    authorized: bool = False
    score: float = 0.0


class Provider(Protocol):
    name: str

    def search(self, request: MediaRequest) -> Iterable[Source]: ...


class SourceEngine:
    """Combines provider results, removes unusable sources and ranks playback."""

    def __init__(self, providers: Iterable[Provider]):
        self.providers = list(providers)

    def search(self, request: MediaRequest) -> List[Source]:
        results: list[Source] = []
        seen: set[str] = set()

        for provider in self.providers:
            try:
                candidates = provider.search(request)
            except Exception:
                continue

            for source in candidates:
                if not source.authorized or not source.url or source.url in seen:
                    continue
                seen.add(source.url)
                source.score = self._score(source)
                results.append(source)

        return sorted(results, key=lambda item: item.score, reverse=True)

    @staticmethod
    def _score(source: Source) -> float:
        quality_score = min(max(source.quality, 0), 2160) / 21.6
        bitrate_score = min(max(source.bitrate_kbps, 0), 30000) / 600.0
        uptime_score = min(max(source.uptime, 0.0), 1.0) * 100
        latency_penalty = min(max(source.latency_ms, 0), 10000) / 200.0
        direct_bonus = 10 if source.direct else 0
        return round(quality_score + bitrate_score + uptime_score + direct_bonus - latency_penalty, 2)
