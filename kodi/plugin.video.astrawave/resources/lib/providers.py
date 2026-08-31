from typing import Iterable

from .source_engine import MediaRequest, Source


class BaseProvider:
    name = "base"

    def search(self, request: MediaRequest) -> Iterable[Source]:
        return []


class PublicDomainProvider(BaseProvider):
    """Adapter point for verified public-domain / reusable VOD catalogs."""

    name = "public-domain"

    def search(self, request: MediaRequest) -> Iterable[Source]:
        # Network-specific implementation is intentionally injected later.
        # Only emit Source(..., authorized=True) after rights verification.
        return []


class UserMediaProvider(BaseProvider):
    """Adapter point for Plex/Jellyfin/Emby/NAS content owned by the user."""

    name = "user-media"

    def search(self, request: MediaRequest) -> Iterable[Source]:
        return []


class UserPlaylistProvider(BaseProvider):
    """Adapter point for user-supplied M3U/M3U8 VOD or live sources."""

    name = "user-playlist"

    def search(self, request: MediaRequest) -> Iterable[Source]:
        return []


class ConnectedProvider(BaseProvider):
    """Adapter point for supported user-connected services, including debrid/cloud accounts."""

    name = "connected-provider"

    def search(self, request: MediaRequest) -> Iterable[Source]:
        return []
