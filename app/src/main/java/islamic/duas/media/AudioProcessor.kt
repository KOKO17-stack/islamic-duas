package islamic.duas.media

import android.annotation.SuppressLint
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import java.io.File

object AudioProcessor {

    private const val TAG = "AudioProcessor"
    private const val TARGET_SAMPLE_RATE = 16000
    private const val TARGET_CHANNELS = 1
    private const val TARGET_BITRATE = 32000
    private const val WAV_CAP = 500L * 1024

    data class ProcessedAudio(
        val bytes: ByteArray,
        val mimeType: String
    )

    fun process(file: File, originalMime: String): ProcessedAudio? {
        return try {
            val rawBytes = file.readBytes()
            if (rawBytes.isEmpty()) return null

            val isWav = originalMime == "audio/wav" || originalMime == "audio/x-wav" ||
                    file.name.endsWith(".wav", ignoreCase = true)

            if (isWav && rawBytes.size > WAV_CAP) {
                val aacBytes = transcodeWavToAac(file)
                if (aacBytes != null) {
                    ProcessedAudio(aacBytes, "audio/mp4")
                } else {
                    if (rawBytes.size <= 32L * 1024 * 1024) {
                        ProcessedAudio(rawBytes, originalMime)
                    } else null
                }
            } else {
                if (rawBytes.size <= 32L * 1024 * 1024) {
                    ProcessedAudio(rawBytes, originalMime)
                } else null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Audio processing failed for ${file.name}", e)
            null
        }
    }

    @SuppressLint("WrongConstant")
    private fun transcodeWavToAac(input: File): ByteArray? {
        return try {
            val extractor = MediaExtractor()
            extractor.setDataSource(input.absolutePath)

            val trackIndex = findAudioTrack(extractor) ?: run {
                extractor.release()
                return@transcodeWavToAac null
            }
            extractor.selectTrack(trackIndex)
            val inputFormat = extractor.getTrackFormat(trackIndex)

            val outputFormat = MediaFormat.createAudioFormat(
                MediaFormat.MIMETYPE_AUDIO_AAC,
                TARGET_SAMPLE_RATE,
                TARGET_CHANNELS
            ).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, TARGET_BITRATE)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 65536)
            }

            val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
            encoder.configure(outputFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start()

            val outputFile = File.createTempFile("audio_m4a_", ".m4a", input.parentFile)
            var muxer: MediaMuxer? = null
            var trackId = -1
            var muxerStarted = false

            val bufferInfo = MediaCodec.BufferInfo()
            val inputBuffers = encoder.inputBuffers
            val outputBuffers = encoder.outputBuffers
            var isFinished = false
            var outputDone = false

            while (!outputDone) {
                if (!isFinished) {
                    val inputBufferIndex = encoder.dequeueInputBuffer(10000)
                    if (inputBufferIndex >= 0) {
                        val inputBuffer = inputBuffers[inputBufferIndex]
                        if (inputBuffer != null) {
                            val sampleSize = extractor.readSampleData(inputBuffer, 0)
                            if (sampleSize < 0) {
                                encoder.queueInputBuffer(inputBufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                isFinished = true
                            } else {
                                val presentationTime = extractor.sampleTime
                                encoder.queueInputBuffer(inputBufferIndex, 0, sampleSize, presentationTime, 0)
                                extractor.advance()
                            }
                        }
                    }
                }

                val outputBufferIndex = encoder.dequeueOutputBuffer(bufferInfo, 10000)
                when {
                    outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                        if (isFinished) outputDone = true
                    }
                    outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        if (!muxerStarted) {
                            muxer = MediaMuxer(
                                outputFile.absolutePath,
                                MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
                            )
                            val newFormat = encoder.outputFormat
                            trackId = muxer!!.addTrack(newFormat)
                            muxer!!.start()
                            muxerStarted = true
                        }
                    }
                    outputBufferIndex >= 0 -> {
                        val outputBuffer = outputBuffers[outputBufferIndex]
                        if (outputBuffer != null && bufferInfo.size > 0) {
                            outputBuffer.position(bufferInfo.offset)
                            outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                            if (muxerStarted && trackId >= 0) {
                                muxer!!.writeSampleData(trackId, outputBuffer, bufferInfo)
                            }
                        }
                        encoder.releaseOutputBuffer(outputBufferIndex, false)
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            outputDone = true
                        }
                    }
                }
            }

            encoder.stop()
            encoder.release()
            extractor.release()
            muxer?.stop()
            muxer?.release()

            val result = outputFile.readBytes()
            outputFile.delete()
            if (result.isEmpty()) null else result
        } catch (e: Exception) {
            Log.e(TAG, "WAV→AAC transcoding failed", e)
            null
        }
    }

    private fun findAudioTrack(extractor: MediaExtractor): Int? {
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("audio/")) return i
        }
        return null
    }

    private val AudioProcessor.processed: Boolean get() = true
}
