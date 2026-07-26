package com.example.util

object RuffleGenerator {
    fun generate(swfPathOrUrl: String, isLocal: Boolean): String {
        // Prepare local or remote URL
        val swfUrl = if (isLocal) {
            "file://$swfPathOrUrl"
        } else {
            swfPathOrUrl
        }

        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                <title>Flash Game Player</title>
                <style>
                    html, body {
                        margin: 0; padding: 0; width: 100%; height: 100%;
                        background-color: #0d1117; overflow: hidden;
                        display: flex; justify-content: center; align-items: center;
                        font-family: sans-serif; color: #fff;
                    }
                    #player-container {
                        width: 100%; height: 100%;
                        position: relative;
                    }
                    #player {
                        width: 100%; height: 100%;
                    }
                    .loader {
                        position: absolute;
                        top: 50%; left: 50%;
                        transform: translate(-50%, -50%);
                        font-size: 16px;
                        font-weight: bold;
                        color: #58a6ff;
                        pointer-events: none;
                        transition: opacity 0.5s ease;
                        text-align: center;
                    }
                </style>
                <!-- Include Ruffle Emulator JS CDN -->
                <script src="https://unpkg.com/@ruffle-rs/ruffle"></script>
            </head>
            <body>
                <div id="player-container">
                    <div id="loader" class="loader">
                        Loading Flash Emulator (Ruffle)...<br>
                        <span style="font-size:12px; font-weight:normal; color:#8b949e;">Requires internet to load the engine on first launch</span>
                    </div>
                    <div id="player"></div>
                </div>
                <script>
                    window.RufflePlayer = window.RufflePlayer || {};
                    window.addEventListener("load", (event) => {
                        const loader = document.getElementById("loader");
                        try {
                            const ruffle = window.RufflePlayer.newest();
                            const player = ruffle.createPlayer();
                            const container = document.getElementById("player");
                            container.appendChild(player);
                            player.load({
                                url: "$swfUrl",
                                allowScriptAccess: true,
                                autocomplete: "on",
                                backgroundColor: "#0d1117"
                            });
                            // Hide loader once load begins
                            setTimeout(() => {
                                loader.style.opacity = "0";
                            }, 3000);
                        } catch (e) {
                            loader.innerHTML = "Error loading Ruffle Flash Emulator: " + e.message;
                        }
                    });
                </script>
            </body>
            </html>
        """.trimIndent()
    }
}
