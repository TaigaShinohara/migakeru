from playwright.sync_api import sync_playwright
import glob

exe = glob.glob("/home/hatch/.cache/ms-playwright/chromium_headless_shell-1248/chrome-headless-shell-linux64/chrome-headless-shell")[0]
with sync_playwright() as p:
    b = p.chromium.launch(executable_path=exe, args=["--no-sandbox", "--force-color-profile=srgb"])
    pg = b.new_page(viewport={"width": 1620, "height": 1000}, device_scale_factor=1)
    pg.goto("file:///home/hatch/workspace/migakeru/design-board/board.html")
    pg.wait_for_timeout(700)
    pg.screenshot(path="/home/hatch/workspace/migakeru/design-board/board.png", full_page=True)
    b.close()
print("done")
