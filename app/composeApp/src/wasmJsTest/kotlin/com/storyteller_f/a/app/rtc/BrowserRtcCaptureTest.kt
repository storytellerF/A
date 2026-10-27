/*
 * This is a private project. All rights reserved.
 */

@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.storyteller_f.a.app.rtc

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.js.JsAny
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BrowserRtcCaptureTest {
    @Test
    fun capturesTracksAndBuildsAudioVideoOffer() =
        runTest {
            withContext(Dispatchers.Main) {
                val fixture = installSyntheticCapture()
                try {
                    val engine = createRtcEngine()
                    val media = engine.openMedia(video = true)
                    try {
                        assertNotNull(media.video)
                        val peer = engine.createPeer(media, RtcPeerEvents({}, {}, {}), this)
                        try {
                            val offer = peer.createOffer()
                            assertTrue(offer.contains("m=audio"))
                            assertTrue(offer.contains("m=video"))
                        } finally {
                            peer.close()
                        }
                    } finally {
                        media.close()
                    }
                    assertTrue(syntheticTracksStopped(fixture))
                } finally {
                    restoreSyntheticCapture(fixture)
                }
            }
        }
}

@JsFun(
    """() => {
        const original = navigator.mediaDevices.getUserMedia;
        const context = new AudioContext();
        const destination = context.createMediaStreamDestination();
        const oscillator = context.createOscillator();
        oscillator.connect(destination);
        oscillator.start();
        const canvas = document.createElement('canvas');
        canvas.width = 640;
        canvas.height = 480;
        canvas.getContext('2d').fillRect(0, 0, 640, 480);
        const audio = destination.stream.getAudioTracks()[0];
        const video = canvas.captureStream(1).getVideoTracks()[0];
        navigator.mediaDevices.getUserMedia = constraints => Promise.resolve(new MediaStream([
            ...(constraints.audio ? [audio] : []), ...(constraints.video ? [video] : [])
        ]));
        return {original, context, oscillator, audio, video};
    }""",
)
private external fun installSyntheticCapture(): JsAny

@JsFun("(fixture) => fixture.audio.readyState === 'ended' && fixture.video.readyState === 'ended'")
private external fun syntheticTracksStopped(fixture: JsAny): Boolean

@JsFun(
    """(fixture) => {
        navigator.mediaDevices.getUserMedia = fixture.original;
        fixture.audio.stop();
        fixture.video.stop();
        fixture.oscillator.stop();
        fixture.context.close();
    }""",
)
private external fun restoreSyntheticCapture(fixture: JsAny)
