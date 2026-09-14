#!/usr/bin/env python3
"""Ping IndexNow (Bing, DuckDuckGo, Yandex, Naver, Seznam share the feed) with
the site's URLs.

Run from anywhere after a publish has gone live on Vercel:

    python landing/indexnow.py                      # every URL in sitemap.xml
    python landing/indexnow.py /tools/tuning-drone.html /blog/some-post.html

Bare paths are prefixed with the host. Google ignores IndexNow entirely; this
is for every other engine and for the AI answer engines that read Bing's index.
The key file must stay at https://metrognome.co.za/<KEY>.txt (it lives next to
this script in landing/ and deploys with the site). Vercel is not part of this:
the script is run by hand, it is not a build step.
"""
import json
import re
import sys
import urllib.request
from pathlib import Path

HOST = "metrognome.co.za"
KEY = "4318e45a30eb0702a19fcca8b66cfc4b"
ENDPOINT = "https://api.indexnow.org/indexnow"

def sitemap_urls() -> list[str]:
    text = (Path(__file__).parent / "sitemap.xml").read_text(encoding="utf-8")
    return re.findall(r"<loc>(.*?)</loc>", text)

def main(argv: list[str]) -> int:
    if argv:
        urls = [u if u.startswith("http") else f"https://{HOST}/{u.lstrip('/')}" for u in argv]
    else:
        urls = sitemap_urls()
    body = json.dumps({
        "host": HOST,
        "key": KEY,
        "keyLocation": f"https://{HOST}/{KEY}.txt",
        "urlList": urls,
    }).encode()
    req = urllib.request.Request(
        ENDPOINT, data=body, method="POST",
        headers={"Content-Type": "application/json; charset=utf-8"},
    )
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            print(f"IndexNow: {r.status} {r.reason} for {len(urls)} URL(s)")
            return 0
    except urllib.error.HTTPError as e:
        print(f"IndexNow HTTP {e.code}: {e.read()[:300]!r}")
        return 1

if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
