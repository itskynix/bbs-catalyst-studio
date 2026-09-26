package mchorse.bbs_mod.audio.mp3;

import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.SampleBuffer;
import mchorse.bbs_mod.audio.Wave;
import mchorse.bbs_mod.resources.Link;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/**
 * Pure Java MP3 reader using JLayer decoder.
 * Converts MP3 audio frames into 16-bit signed PCM Wave data.
 */
public class Mp3Reader
{
    public static Wave read(Link link, InputStream stream) throws Exception
    {
        return read(stream);
    }

    public static Wave read(InputStream stream) throws Exception
    {
        Bitstream bitstream = new Bitstream(stream);
        Decoder decoder = new Decoder();

        Header header = bitstream.readFrame();

        if (header == null)
        {
            throw new IllegalArgumentException("Empty or invalid MP3 audio stream!");
        }

        int channels = (header.mode() == Header.SINGLE_CHANNEL) ? 1 : 2;
        int sampleRate = header.frequency();

        ByteArrayOutputStream baos = new ByteArrayOutputStream(64 * 1024);

        try
        {
            while (header != null)
            {
                SampleBuffer output = (SampleBuffer) decoder.decodeFrame(header, bitstream);

                if (output != null)
                {
                    short[] samples = output.getBuffer();
                    int len = output.getBufferLength();

                    for (int i = 0; i < len; i++)
                    {
                        short s = samples[i];

                        baos.write(s & 0xFF);
                        baos.write((s >> 8) & 0xFF);
                    }
                }

                bitstream.closeFrame();

                header = bitstream.readFrame();
            }
        }
        catch (Exception e)
        {
            /* If we already decoded audio frames, ignore trailing corrupt frames or ID3v1 tags */
            if (baos.size() == 0)
            {
                throw e;
            }
        }
        finally
        {
            try
            {
                bitstream.close();
            }
            catch (Exception ignored)
            {}
        }

        byte[] pcmData = baos.toByteArray();

        return new Wave(1, channels, sampleRate, 16, pcmData);
    }
}
