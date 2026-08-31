# AstraWave Kodi — TV Guide + Sports Architecture

## Goal
Provide a polished TV guide using verified free/authorized live streams and an event-first sports schedule that maps events to playable authorized channels when available.

## TV Guide Pipeline
1. Ingest candidate live channels from approved public/official sources and user-connected playlists.
2. Normalize channel names, IDs, logos, country, language, and category.
3. Match XMLTV/EPG IDs.
4. Health-check stream URLs on a schedule.
5. Maintain primary + backup source candidates per channel.
6. Generate an AstraWave M3U and XMLTV output for Kodi PVR IPTV Simple.
7. Hide unhealthy or unverified default sources from the user-facing guide.

## Channel Model
- channel_id
- display_name
- callsign
- country
- region
- category
- logo_url
- xmltv_id
- source_type
- stream_url
- stream_format
- quality
- bitrate
- latency_ms
- health_score
- last_checked_at
- authorization_status
- is_default

## Guide UX
Sections:
- Favorites
- Recently Watched
- Local
- News
- Sports
- Entertainment
- Movies
- Kids
- Weather
- Music
- International

Each channel should show:
- channel logo/name
- current program
- next program
- progress bar
- stream quality/health badge
- Watch button

## Sports Schedule Pipeline
Use a sports metadata API for schedules and TV-broadcast metadata. Normalize events into AstraWave's own event model.

Event fields:
- event_id
- league
- sport
- home_team
- away_team
- event_name
- start_time_utc
- venue
- status
- broadcaster_names[]
- candidate_channel_ids[]
- playable_channel_ids[]
- official_free_links[]

## Sports Matching Logic
1. Fetch today's/upcoming events.
2. Fetch broadcaster/channel metadata for each event where available.
3. Normalize broadcaster names (e.g. CBS Sports Network vs CBSSN).
4. Match broadcaster aliases against AstraWave's channel inventory.
5. Validate candidate channels are healthy and authorized for playback.
6. Rank matches by region, user location, source health, and quality.
7. Show Watch only when a permitted playable source exists.

## Sports UX
Top-level sections:
- Live Now
- Starting Soon
- Today
- Tomorrow
- NFL
- NBA
- MLB
- NHL
- NCAA
- Soccer
- Combat Sports
- Motorsports
- More

Event card:
- teams/event title
- league
- start time
- status
- broadcaster(s)
- available AstraWave channel(s)
- Watch / Remind Me / Add to Favorites

## Source Rules
Default AstraWave feeds must be official, public-domain, licensed, or otherwise permitted for third-party playback. User-supplied playlists are kept separate from AstraWave-provided sources. Do not promote an unknown public URL to a default channel merely because it resolves.

## Kodi Integration
Use Kodi PVR IPTV Simple for the native guide when possible. AstraWave's backend produces cleaned M3U/XMLTV data and Kodi handles PVR rendering/playback.

For sports, the Kodi plugin renders an event-first hub and links each event to the matched channel entry or authorized direct event stream.

## Backend Endpoints
- GET /v1/live/channels
- GET /v1/live/groups
- GET /v1/live/epg
- GET /v1/live/channel/{channel_id}
- GET /v1/sports/today
- GET /v1/sports/upcoming
- GET /v1/sports/event/{event_id}
- GET /v1/sports/event/{event_id}/watch
- GET /v1/exports/astrawave.m3u
- GET /v1/exports/astrawave.xmltv

## Initial Data Sources
- IPTV-org style channel/EPG datasets only after source-level validation and rights review
- TheSportsDB for schedules and broadcaster metadata
- official broadcaster feeds and other explicitly permitted free streams
- user-connected M3U/XMLTV sources

## Reliability
Store rolling health metrics per stream:
- success_rate_24h
- success_rate_7d
- median_startup_ms
- consecutive_failures
- last_success_at
- last_failure_at

Failover policy:
- try highest-ranked healthy primary
- retry once on transient network failure
- move to verified backup
- mark degraded after repeated failures
- never expose raw backend errors to users
