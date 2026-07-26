package io.github.niubima.endpoemfabric.client;

import io.github.niubima.endpoemfabric.Endpoemfabric;
import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.BitstreamException;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.DecoderException;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.SampleBuffer;
import net.fabricmc.fabric.api.client.sound.v1.FabricSoundInstance;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.JOrbisAudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Util;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * A streamed sound instance backed by one immutable file path. The class feeds
 * Minecraft PCM data, so the normal Music volume setting and audio device are
 * still used for every supported source format.
 */
final class CustomEndPoemMusicSound extends AbstractSoundInstance implements FabricSoundInstance {
    private static final Identifier SOUND_ID = Identifier.fromNamespaceAndPath(
            Endpoemfabric.MODID,
            "custom_end_poem_music"
    );
    private static final int MAX_READ_BYTES = 1_048_576;

    private final Path path;
    private final BiConsumer<CustomEndPoemMusicSound, Throwable> failureListener;
    private final AtomicBoolean failureReported = new AtomicBoolean();

    CustomEndPoemMusicSound(
            Path path,
            BiConsumer<CustomEndPoemMusicSound, Throwable> failureListener
    ) {
        super(SOUND_ID, SoundSource.MUSIC, SoundInstance.createUnseededRandom());
        this.path = path;
        this.failureListener = failureListener;
        volume = 1.0F;
        pitch = 1.0F;
        looping = false;
        delay = 0;
        attenuation = Attenuation.NONE;
        relative = true;
    }

    static void validate(Path path) throws IOException {
        try (AudioStream stream = FileAudioStream.open(path)) {
            AudioFormat format = validateFormat(stream.getFormat());
            if (stream.read(Math.max(format.getFrameSize(), 4_096)) == null) {
                throw new IOException("Audio file contains no decodable samples");
            }
        }
    }

    @Override
    public CompletableFuture<AudioStream> getAudioStream(
            SoundBufferLibrary soundBuffers,
            Identifier soundId,
            boolean repeatInstantly
    ) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return new LoopingFileAudioStream(path, this::reportFailure);
            } catch (IOException | RuntimeException e) {
                reportFailure(e);
                throw new CompletionException(e);
            }
        }, Util.nonCriticalIoPool());
    }

    private void reportFailure(Throwable error) {
        if (failureReported.compareAndSet(false, true)) {
            failureListener.accept(this, error);
        }
    }

    private static AudioFormat validateFormat(AudioFormat format) throws IOException {
        if (!AudioFormat.Encoding.PCM_SIGNED.equals(format.getEncoding())
                || format.getSampleSizeInBits() != 16
                || format.isBigEndian()
                || format.getChannels() < 1
                || format.getChannels() > 2
                || format.getSampleRate() <= 0
                || format.getFrameSize() != format.getChannels() * 2) {
            throw new IOException("Unsupported PCM output format: " + format);
        }
        return format;
    }

    private static boolean formatsMatch(AudioFormat left, AudioFormat right) {
        return left.getChannels() == right.getChannels()
                && left.getSampleSizeInBits() == right.getSampleSizeInBits()
                && left.getFrameSize() == right.getFrameSize()
                && left.getSampleRate() == right.getSampleRate()
                && left.isBigEndian() == right.isBigEndian()
                && left.getEncoding().equals(right.getEncoding());
    }

    private static int alignedReadSize(int requestedBytes, AudioFormat format) {
        int frameSize = format.getFrameSize();
        int capped = Math.min(Math.max(requestedBytes, frameSize), MAX_READ_BYTES);
        return Math.max(frameSize, capped - capped % frameSize);
    }

    private static void transfer(ByteBuffer source, ByteBuffer target) {
        int length = Math.min(source.remaining(), target.remaining());
        int originalLimit = source.limit();
        source.limit(source.position() + length);
        target.put(source);
        source.limit(originalLimit);
    }

    private static final class LoopingFileAudioStream implements AudioStream {
        private final Path path;
        private final Consumer<Throwable> failureListener;
        private final AudioFormat format;
        private AudioStream current;
        private ByteBuffer pending;
        private boolean closed;

        private LoopingFileAudioStream(Path path, Consumer<Throwable> failureListener) throws IOException {
            this.path = path;
            this.failureListener = failureListener;
            current = FileAudioStream.open(path);
            try {
                format = validateFormat(current.getFormat());
            } catch (IOException e) {
                current.close();
                throw e;
            }
        }

        @Override
        public AudioFormat getFormat() {
            return format;
        }

        @Override
        public ByteBuffer read(int requestedBytes) throws IOException {
            if (closed) {
                return null;
            }

            try {
                ByteBuffer output = ByteBuffer.allocateDirect(alignedReadSize(requestedBytes, format))
                        .order(ByteOrder.LITTLE_ENDIAN);
                int emptyLoops = 0;
                while (output.hasRemaining()) {
                    if (pending == null || !pending.hasRemaining()) {
                        pending = current.read(output.remaining());
                    }
                    if (pending == null || !pending.hasRemaining()) {
                        if (output.position() == 0 && ++emptyLoops > 1) {
                            throw new IOException("Audio file contains no decoded samples");
                        }
                        reopen();
                        continue;
                    }

                    emptyLoops = 0;
                    transfer(pending, output);
                }
                output.flip();
                return output;
            } catch (IOException | RuntimeException e) {
                failureListener.accept(e);
                throw e;
            }
        }

        @Override
        public void close() throws IOException {
            if (!closed) {
                closed = true;
                current.close();
            }
        }

        private void reopen() throws IOException {
            current.close();
            AudioStream replacement = FileAudioStream.open(path);
            try {
                if (!formatsMatch(format, validateFormat(replacement.getFormat()))) {
                    throw new IOException("Audio format changed while looping " + path.getFileName());
                }
                current = replacement;
                pending = null;
            } catch (IOException | RuntimeException e) {
                replacement.close();
                throw e;
            }
        }
    }

    private static final class FileAudioStream {
        private FileAudioStream() {
        }

        private static AudioStream open(Path path) throws IOException {
            String fileName = path.getFileName().toString();
            int extensionStart = fileName.lastIndexOf('.');
            String extension = extensionStart < 0
                    ? ""
                    : fileName.substring(extensionStart + 1).toLowerCase(Locale.ROOT);
            return switch (extension) {
                case "ogg" -> openOgg(path);
                case "wav", "wave" -> openWav(path);
                case "mp3" -> new Mp3AudioStream(path);
                default -> throw new IOException("Unsupported custom music format: " + fileName);
            };
        }

        private static AudioStream openOgg(Path path) throws IOException {
            InputStream input = Files.newInputStream(path);
            try {
                return new JOrbisAudioStream(input);
            } catch (IOException | RuntimeException e) {
                input.close();
                throw e;
            }
        }

        private static AudioStream openWav(Path path) throws IOException {
            AudioInputStream source;
            try {
                source = AudioSystem.getAudioInputStream(path.toFile());
            } catch (UnsupportedAudioFileException e) {
                throw new IOException("Unsupported WAV file", e);
            }

            try {
                return new JavaSoundPcmAudioStream(toPcm(source));
            } catch (IOException | RuntimeException e) {
                source.close();
                throw e;
            }
        }

        private static AudioInputStream toPcm(AudioInputStream source) throws IOException {
            AudioFormat sourceFormat = source.getFormat();
            int channels = sourceFormat.getChannels();
            float sampleRate = sourceFormat.getSampleRate();
            if (channels < 1 || channels > 2 || sampleRate <= 0) {
                throw new IOException("Only mono and stereo audio files are supported");
            }

            AudioFormat targetFormat = new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    sampleRate,
                    16,
                    channels,
                    channels * 2,
                    sampleRate,
                    false
            );
            if (!AudioSystem.isConversionSupported(targetFormat, sourceFormat)) {
                throw new IOException("Cannot convert audio to 16-bit PCM");
            }
            return AudioSystem.getAudioInputStream(targetFormat, source);
        }
    }

    private static final class JavaSoundPcmAudioStream implements AudioStream {
        private final AudioInputStream input;
        private final AudioFormat format;

        private JavaSoundPcmAudioStream(AudioInputStream input) throws IOException {
            this.input = input;
            format = validateFormat(input.getFormat());
        }

        @Override
        public AudioFormat getFormat() {
            return format;
        }

        @Override
        public ByteBuffer read(int requestedBytes) throws IOException {
            byte[] data = new byte[alignedReadSize(requestedBytes, format)];
            int length = 0;
            while (length < data.length) {
                int read = input.read(data, length, data.length - length);
                if (read <= 0) {
                    break;
                }
                length += read;
            }
            if (length == 0) {
                return null;
            }

            ByteBuffer buffer = ByteBuffer.allocateDirect(length).order(ByteOrder.LITTLE_ENDIAN);
            buffer.put(data, 0, length);
            buffer.flip();
            return buffer;
        }

        @Override
        public void close() throws IOException {
            input.close();
        }
    }

    private static final class Mp3AudioStream implements AudioStream {
        private final Bitstream bitstream;
        private final Decoder decoder = new Decoder();
        private final AudioFormat format;
        private ByteBuffer pending;
        private boolean closed;

        private Mp3AudioStream(Path path) throws IOException {
            InputStream input = Files.newInputStream(path);
            bitstream = new Bitstream(input);
            try {
                DecodedFrame firstFrame = decodeFrame();
                if (firstFrame == null) {
                    throw new IOException("MP3 file contains no decodable frames");
                }
                format = validateFormat(firstFrame.format());
                pending = firstFrame.samples();
            } catch (IOException | RuntimeException e) {
                try {
                    bitstream.close();
                } catch (BitstreamException closeError) {
                    e.addSuppressed(closeError);
                }
                throw e;
            }
        }

        @Override
        public AudioFormat getFormat() {
            return format;
        }

        @Override
        public ByteBuffer read(int requestedBytes) throws IOException {
            if (closed) {
                return null;
            }

            ByteBuffer output = ByteBuffer.allocateDirect(alignedReadSize(requestedBytes, format))
                    .order(ByteOrder.LITTLE_ENDIAN);
            while (output.hasRemaining()) {
                if (pending == null || !pending.hasRemaining()) {
                    DecodedFrame nextFrame = decodeFrame();
                    if (nextFrame == null) {
                        break;
                    }
                    if (!formatsMatch(format, validateFormat(nextFrame.format()))) {
                        throw new IOException("MP3 audio format changed within the file");
                    }
                    pending = nextFrame.samples();
                }
                transfer(pending, output);
            }
            if (output.position() == 0) {
                return null;
            }

            output.flip();
            return output;
        }

        @Override
        public void close() throws IOException {
            if (!closed) {
                closed = true;
                try {
                    bitstream.close();
                } catch (BitstreamException e) {
                    throw new IOException("Failed to close MP3 stream", e);
                }
            }
        }

        private DecodedFrame decodeFrame() throws IOException {
            try {
                Header frame = bitstream.readFrame();
                if (frame == null) {
                    return null;
                }
                try {
                    SampleBuffer samples = (SampleBuffer) decoder.decodeFrame(frame, bitstream);
                    int channels = samples.getChannelCount();
                    int sampleRate = samples.getSampleFrequency();
                    if (channels < 1 || channels > 2 || sampleRate <= 0) {
                        throw new IOException("Unsupported MP3 channel layout");
                    }

                    short[] source = samples.getBuffer();
                    int length = samples.getBufferLength();
                    ByteBuffer pcm = ByteBuffer.allocateDirect(length * Short.BYTES)
                            .order(ByteOrder.LITTLE_ENDIAN);
                    for (int i = 0; i < length; i++) {
                        pcm.putShort(source[i]);
                    }
                    pcm.flip();
                    AudioFormat decodedFormat = new AudioFormat(
                            AudioFormat.Encoding.PCM_SIGNED,
                            sampleRate,
                            16,
                            channels,
                            channels * 2,
                            sampleRate,
                            false
                    );
                    return new DecodedFrame(pcm, decodedFormat);
                } finally {
                    bitstream.closeFrame();
                }
            } catch (BitstreamException | DecoderException e) {
                throw new IOException("Failed to decode MP3 audio", e);
            }
        }
    }

    private record DecodedFrame(ByteBuffer samples, AudioFormat format) {
    }
}
