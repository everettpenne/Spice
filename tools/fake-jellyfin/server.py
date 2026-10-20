#!/usr/bin/env python3
"""A tiny stand-in for a Jellyfin server, enough for Spice's screens: sign in (demo / demo), libraries, shelves, search, seasons and
episodes, a music library (artists, albums and songs), generated artwork, a playable test video and audio file with HTTP range support,
and playback reports (printed, so a test can see them).

    python3 tools/fake-jellyfin/server.py [port]      # default 8096; the emulator reaches it at http://10.0.2.2:8096

The video is made with ffmpeg on first run (a 40 s test pattern, H.264 + AAC, with an embedded subtitle track)."""
import json, os, re, subprocess, sys, threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlparse, parse_qs
from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
VIDEO = os.path.join(HERE, "test.mp4")
TOKEN, USER_ID, SERVER_ID = "tok-demo", "u1", "srv1"
PORT = int(sys.argv[1]) if len(sys.argv) > 1 else 8096
TICKS = 10_000_000

DTS = os.path.join(HERE, "dts.mkv")
HLS = os.path.join(HERE, "hls")
AUDIO = os.path.join(HERE, "song.wav")

def make_video():
    """Makes (once) the test media: test.mp4 (H.264 + AAC + a subtitle track, 40 s), dts.mkv (DTS audio, which a phone cannot decode, so Spice
    must fall back to a transcode), hls/ (what the server's transcode would send: H.264 + AAC segments) and song.wav (a 10 s sine wave every
    music track serves, written with the stdlib so it needs no ffmpeg)."""
    srt = os.path.join(HERE, "test.srt")
    open(srt, "w").write("1\n00:00:01,000 --> 00:00:06,000\nHello from the fake server\n\n2\n00:00:08,000 --> 00:00:14,000\nSubtitles work\n")
    run = lambda *a: subprocess.run(["ffmpeg", "-y", "-loglevel", "error", *a], check=True)
    if not os.path.exists(VIDEO):
        run("-f", "lavfi", "-i", "testsrc=size=1280x720:rate=24:duration=40", "-f", "lavfi", "-i", "sine=frequency=440:duration=40", "-i", srt,
            "-map", "0:v", "-map", "1:a", "-map", "2:s", "-c:v", "libx264", "-pix_fmt", "yuv420p", "-c:a", "aac", "-c:s", "mov_text", "-metadata:s:s:0", "language=eng", VIDEO)
    if not os.path.exists(DTS): run("-i", VIDEO, "-t", "20", "-map", "0:v", "-map", "0:a", "-c:v", "copy", "-c:a", "dca", "-strict", "-2", DTS)
    if not os.path.exists(HLS):
        os.makedirs(HLS)
        run("-i", VIDEO, "-map", "0:v", "-map", "0:a", "-c:v", "libx264", "-preset", "veryfast", "-pix_fmt", "yuv420p", "-c:a", "aac", "-f", "hls", "-hls_time", "4",
            "-hls_playlist_type", "vod", "-hls_segment_filename", os.path.join(HLS, "seg%d.ts"), os.path.join(HLS, "index.m3u8"))
    if not os.path.exists(AUDIO):
        import math, struct, wave
        rate, seconds, freq = 44100, 10, 440
        w = wave.open(AUDIO, "w"); w.setnchannels(1); w.setsampwidth(2); w.setframerate(rate)
        w.writeframes(b"".join(struct.pack("<h", int(12000 * math.sin(2 * math.pi * freq * i / rate))) for i in range(rate * seconds)))
        w.close()

def item(id, name, type, **kw):
    d = {"Id": id, "Name": name, "Type": type, "ImageTags": {"Primary": "p", "Thumb": "t"}, "BackdropImageTags": ["b"], "UserData": {"PlaybackPositionTicks": 0, "Played": False}}
    d.update(kw); return d

SRC = lambda id: [{"Id": "ms-" + id, "Name": "Test", "Container": "mp4", "RunTimeTicks": 40 * TICKS, "SupportsDirectPlay": True, "SupportsDirectStream": True,
                    "MediaStreams": [{"Type": "Video", "Index": 0, "Codec": "h264"}, {"Type": "Audio", "Index": 1, "Codec": "aac", "Language": "eng"},
                                     {"Type": "Subtitle", "Index": 2, "Codec": "mov_text", "Language": "eng"}]}]
LOREM = "A long and winding overview that wraps over several lines so the detail page has real text to lay out, with a few clauses and a full stop at the end."
ITEMS = {}
def add(i): ITEMS[i["Id"]] = i; return i

add(item("lib-m", "Movies", "CollectionFolder", CollectionType="movies", IsFolder=True))
add(item("lib-s", "Shows", "CollectionFolder", CollectionType="tvshows", IsFolder=True))
titles = ["Dune", "Arrival", "Blade Runner 2049", "Sicario", "Prisoners", "Enemy"]
for n, t in enumerate(titles):
    add(item(f"m{n}", t, "Movie", ProductionYear=2010 + n, Overview=LOREM, RunTimeTicks=40 * TICKS, CommunityRating=7.0 + n / 10, OfficialRating="PG-13",
             Genres=["Sci-Fi", "Drama"], MediaSources=SRC(f"m{n}"), ParentId="lib-m", DateCreated=f"2026-0{n+1}-01",
             People=[{"Id": f"pp{n}", "Name": "An Actor", "Role": "Hero", "Type": "Actor"}]))
add(item("m6", "Needs Transcoding", "Movie", ProductionYear=2019, Overview="Its audio is DTS, which phones cannot decode, so Spice has to ask the server to convert it.", RunTimeTicks=20 * TICKS,
         CommunityRating=6.5, Genres=["Test"], MediaSources=[dict(SRC("m6")[0], Container="mkv", MediaStreams=[{"Type": "Video", "Index": 0, "Codec": "h264"}, {"Type": "Audio", "Index": 1, "Codec": "dts", "Language": "eng"}])],
         ParentId="lib-m", DateCreated="2026-07-01"))
ITEMS["m1"]["UserData"] = {"PlaybackPositionTicks": 16 * TICKS, "PlayedPercentage": 40.0, "Played": False}
for s, name in enumerate(["Severance", "Andor"]):
    sid = f"s{s}"
    add(item(sid, name, "Series", ProductionYear=2022, Overview=LOREM, Genres=["Drama"], IsFolder=True, ChildCount=2, ParentId="lib-s",
             UserData={"PlaybackPositionTicks": 0, "Played": False, "UnplayedItemCount": 5}))
    for se in (1, 2):
        add(item(f"{sid}-se{se}", f"Season {se}", "Season", SeriesId=sid, SeriesName=name, IndexNumber=se, IsFolder=True, ParentId=sid))
        for ep in (1, 2, 3):
            add(item(f"{sid}-{se}-{ep}", f"Episode title {ep}", "Episode", SeriesId=sid, SeriesName=name, SeasonId=f"{sid}-se{se}", IndexNumber=ep, ParentIndexNumber=se,
                     Overview=LOREM, RunTimeTicks=40 * TICKS, MediaSources=SRC(f"{sid}-{se}-{ep}"), SeriesPrimaryImageTag="p"))
ITEMS["s0-1-2"]["UserData"] = {"PlaybackPositionTicks": 10 * TICKS, "PlayedPercentage": 25.0, "Played": False}
ITEMS["s0-1-1"]["UserData"]["Played"] = True

# Music: a library with artists, albums and songs (every song serves the same 10 s sine-wave wav), enough for the Music tab's screens.
AUDIO_SRC = lambda id: [{"Id": "ms-" + id, "Container": "wav", "RunTimeTicks": 10 * TICKS, "SupportsDirectPlay": True, "SupportsDirectStream": True,
                          "MediaStreams": [{"Type": "Audio", "Index": 0, "Codec": "pcm_s16le"}]}]
ALBUM_ARTIST = {}   # album id -> artist item id, for the ArtistIds filter
add(item("lib-mu", "Music", "CollectionFolder", CollectionType="music", IsFolder=True))
_artist_names = ["Glass Animals", "Tame Impala", "Radiohead"]
for a, name in enumerate(_artist_names):
    add(item(f"ar{a}", name, "MusicArtist", ParentId="lib-mu", Overview=LOREM))
_albums = [("Zaba", "ar0", 2014), ("Currents", "ar1", 2015), ("In Rainbows", "ar2", 2007)]
for a, (album_name, artist_id, year) in enumerate(_albums):
    aid = f"al{a}"; artist_name = ITEMS[artist_id]["Name"]
    add(item(aid, album_name, "MusicAlbum", ParentId="lib-mu", ProductionYear=year, AlbumArtists=[artist_name], Artists=[artist_name],
             Overview=LOREM, Genres=["Rock"], DateCreated=f"2026-0{a+1}-01"))
    ALBUM_ARTIST[aid] = artist_id
    for t in range(1, 6):
        add(item(f"{aid}-t{t}", f"{album_name} — track {t}", "Audio", ParentId=aid, AlbumId=aid, Album=album_name,
                 AlbumArtists=[artist_name], Artists=[artist_name], IndexNumber=t, RunTimeTicks=10 * TICKS,
                 ImageTags={}, AlbumPrimaryImageTag="p", MediaSources=AUDIO_SRC(f"{aid}-t{t}")))

favorites, REPORTS = set(), []

def listing(items): return {"Items": items, "TotalRecordCount": len(items)}

class H(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"
    def log_message(self, *a): pass
    def send(self, code, body=b"", ctype="application/json", extra=None):
        if not isinstance(body, bytes): body = json.dumps(body).encode()
        self.send_response(code); self.send_header("Content-Type", ctype); self.send_header("Content-Length", str(len(body)))
        for k, v in (extra or {}).items(): self.send_header(k, v)
        self.end_headers(); self.wfile.write(body)
    def authed(self): return f'Token="{TOKEN}"' in self.headers.get("Authorization", "")
    def do_GET(self): self.route("GET")
    def do_POST(self): self.route("POST")
    def do_DELETE(self): self.route("DELETE")
    def route(self, method):
        u = urlparse(self.path); q = {k: v[0] for k, v in parse_qs(u.query).items()}; p = u.path
        print("REQ", method, self.path[:140], flush=True)
        n = int(self.headers.get("Content-Length") or 0); body = self.rfile.read(n) if n else b""
        if p == "/System/Info/Public": return self.send(200, {"ServerName": "Fake Jellyfin", "Version": "10.10.0", "Id": SERVER_ID, "ProductName": "Jellyfin Server"})
        if p == "/Users/AuthenticateByName":
            c = json.loads(body or b"{}")
            if c.get("Username") == "demo" and c.get("Pw") == "demo": return self.send(200, {"User": {"Id": USER_ID, "Name": "demo"}, "AccessToken": TOKEN, "ServerId": SERVER_ID})
            return self.send(401)
        if p.startswith("/Items/") and "/Images/" in p:
            return self.send(200, self.art(p.split("/")[2], "Backdrop" in p), "image/jpeg")   # images are open, as on a real server
        if not self.authed(): return self.send(401)
        if p.startswith("/Sessions/"):
            REPORTS.append((p, json.loads(body or b"{}"))); print("REPORT", p, body.decode(), flush=True); return self.send(204)
        m = re.match(r"/Users/[^/]+/(PlayedItems|FavoriteItems)/(.+)", p)
        if m:
            if m[1] == "FavoriteItems": (favorites.add if method == "POST" else favorites.discard)(m[2])
            else: ITEMS[m[2]]["UserData"]["Played"] = method == "POST"
            print("STATE", method, p, flush=True); return self.send(200, {})
        if p.endswith("/Views"): return self.send(200, listing([ITEMS["lib-m"], ITEMS["lib-s"], ITEMS["lib-mu"]]))
        if p.endswith("/Items/Resume"): return self.send(200, listing([ITEMS["m1"], ITEMS["s0-1-2"]]))
        if p == "/Shows/NextUp": return self.send(200, listing([ITEMS["s0-1-3"], ITEMS["s1-1-1"]]))
        if p.endswith("/Artists"):
            pool = [i for i in ITEMS.values() if i["Type"] == "MusicArtist" and (not q.get("ParentId") or i.get("ParentId") == q["ParentId"])]
            return self.send(200, listing(pool))
        if p.endswith("/Items/Latest"):
            pool = [i for i in ITEMS.values() if i.get("ParentId") == q.get("ParentId")]
            if q.get("IncludeItemTypes"): pool = [i for i in pool if i["Type"] in q["IncludeItemTypes"].split(",")]
            return self.send(200, pool[:16])
        m = re.match(r"/Shows/([^/]+)/Seasons", p)
        if m: return self.send(200, listing([i for i in ITEMS.values() if i["Type"] == "Season" and i["SeriesId"] == m[1]]))
        m = re.match(r"/Shows/([^/]+)/Episodes", p)
        if m: return self.send(200, listing([i for i in ITEMS.values() if i["Type"] == "Episode" and i["SeriesId"] == m[1] and (not q.get("SeasonId") or i["SeasonId"] == q["SeasonId"])]))
        if p.endswith("/Similar"): return self.send(200, listing([i for i in ITEMS.values() if i["Type"] == "Movie"][:5]))
        m = re.match(r"/Users/[^/]+/Items/(.+)", p)
        if m and m[1] in ITEMS:
            it = dict(ITEMS[m[1]]); it["UserData"] = dict(it["UserData"], IsFavorite=m[1] in favorites); return self.send(200, it)
        if p.endswith("/Items"):
            pool = list(ITEMS.values())
            if q.get("ParentId"): pool = [i for i in pool if i.get("ParentId") == q["ParentId"]]
            if q.get("IncludeItemTypes"): pool = [i for i in pool if i["Type"] in q["IncludeItemTypes"].split(",")]
            if q.get("ArtistIds"): pool = [i for i in pool if ALBUM_ARTIST.get(i["Id"]) in q["ArtistIds"].split(",")]
            if q.get("SearchTerm"): pool = [i for i in pool if q["SearchTerm"].lower() in i["Name"].lower()]
            if q.get("Filters") == "IsFavorite": pool = [i for i in pool if i["Id"] in favorites]
            if not q.get("ParentId") and not q.get("IncludeItemTypes") and not q.get("Filters"): pool = [i for i in pool if i["Type"] in ("Movie", "Series")]
            by = q.get("SortBy", "SortName").split(",")[0]
            key = {"ProductionYear": lambda i: i.get("ProductionYear", 0), "CommunityRating": lambda i: i.get("CommunityRating", 0), "DateCreated": lambda i: i.get("DateCreated", "")}.get(by, lambda i: i["Name"])
            pool.sort(key=key, reverse=q.get("SortOrder") == "Descending")
            s, l = int(q.get("StartIndex", 0)), int(q.get("Limit", 60))
            return self.send(200, {"Items": pool[s:s + l], "TotalRecordCount": len(pool)})
        m = re.match(r"/Items/([^/]+)/Download", p)                                    # the original file, for saving to the phone
        if m and m[1] in ITEMS: return self.video(AUDIO, "audio/wav") if ITEMS[m[1]]["Type"] == "Audio" else self.video(VIDEO)
        if re.match(r"/Audio/[^/]+/(stream|universal)", p): return self.video(AUDIO, "audio/wav")
        m = re.match(r"/Videos/[^/]+/(stream|master\.m3u8)", p)
        if m and m[1] == "stream": return self.video(DTS if "/m6/" in p else VIDEO)
        if m: return self.send(200, open(os.path.join(HLS, "index.m3u8"), "rb").read(), "application/vnd.apple.mpegurl")
        m = re.match(r"/Videos/[^/]+/(seg\d+\.ts)", p)
        if m: return self.send(200, open(os.path.join(HLS, m[1]), "rb").read(), "video/mp2t")
        return self.send(404)
    def art(self, id, wide):
        import io
        w, h = (640, 360) if wide else (400, 600)
        hue = sum(map(ord, id)) % 360
        img = Image.new("RGB", (w, h)); d = ImageDraw.Draw(img)
        for y in range(h): d.line([(0, y), (w, y)], fill=tuple(int(c) for c in __import__("colorsys").hsv_to_rgb(hue / 360, .55, .35 + .4 * y / h) and [x * 255 for x in __import__("colorsys").hsv_to_rgb(hue / 360, .55, .35 + .4 * y / h)]))
        d.text((16, 16), ITEMS.get(id, {}).get("Name", id), fill="white")
        buf = io.BytesIO(); img.save(buf, "JPEG"); return buf.getvalue()
    def video(self, path, ctype="video/mp4"):
        size = os.path.getsize(path); rng = self.headers.get("Range"); start, end = 0, size - 1
        if rng and (m := re.match(r"bytes=(\d*)-(\d*)", rng)):
            if m[1]: start = int(m[1])
            if m[2]: end = int(m[2])
        with open(path, "rb") as f: f.seek(start); data = f.read(end - start + 1)
        extra = {"Accept-Ranges": "bytes"}
        if rng: extra["Content-Range"] = f"bytes {start}-{end}/{size}"
        self.send(206 if rng else 200, data, ctype, extra)

if __name__ == "__main__":
    make_video()
    print(f"Fake Jellyfin on :{PORT}  (user demo / demo)", flush=True)
    ThreadingHTTPServer(("0.0.0.0", PORT), H).serve_forever()
