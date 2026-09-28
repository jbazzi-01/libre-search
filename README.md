# Libre Search

A personal, privacy-first Android search app with a Google-style interface.

- Web, images, videos, shopping and forum results from the Brave Search API (raw results only, no AI answers)
- Knowledge panels: movies and shows (TMDB: cast, director, where to watch), people and topics (Wikipedia + Wikidata)
- Weather card (Open-Meteo) with hourly and 8-day forecast and an illustrated scene
- Local places from OpenStreetMap with map, opening hours, call and directions
- Calculator and unit conversion
- News and good news from a curated list of independent left, communist, anarchist and Palestine outlets (RSS), editable in Settings
- AI-generated images hidden, Israeli and pro-Israel outlets hidden (both switchable)
- Built-in browser: tabs, private tabs, history, bookmarks, downloads, find in page, desktop mode, ad/tracker/cookie-banner blocking
- Ad-free video player (YouTube, PeerTube, SoundCloud, Bandcamp) with background play, picture-in-picture, speed, quality and download

## Keys

Enter these in the app's Settings (they stay on the phone):

- Brave Search API key: https://api-dashboard.search.brave.com
- TMDB API key: https://www.themoviedb.org/settings/api

## Building

Every push to `main` builds a signed APK with GitHub Actions and publishes it under Releases.

The video player uses the NewPipe Extractor and a data source adapted from NewPipe (GPL-3.0).
