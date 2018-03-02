package com.ociweb.behaviors;

import com.ociweb.iot.maker.FogCommandChannel;
import com.ociweb.iot.maker.FogRuntime;
import com.ociweb.iot.maker.SerialListener;
import com.ociweb.pronghorn.pipe.ChannelReader;

import static com.ociweb.schema.MessageScheme.messageSize;

public class UARTMessageWindowBehavior implements SerialListener {
    private final String publishTopic;
    private final FogCommandChannel channel;
    private final byte[] buffer;
    private long timeStamp = -1;

    private int inTheKeyOf2(int number) {
        double d = Math.log(number) / Math.log(2);
        int p = (int)Math.ceil(d);
        return (int)Math.pow(2, p);
    }

    public UARTMessageWindowBehavior(FogRuntime runtime, String publishTopic) {
        this.publishTopic = publishTopic;
        // Make certain we can fit at least one complete message
        int bufferSize = inTheKeyOf2(messageSize * 2);
        this.channel = runtime.newCommandChannel(DYNAMIC_MESSAGING, bufferSize);
        this.buffer = new byte[bufferSize];
    }

    @Override
    public int message(ChannelReader serialReader) {
        // Read what we can
        final int len = serialReader.read(buffer);
        // Find beginning of message
        int begin = -1;
        for (int s = 0; s < len; s++) {
            if (buffer[s] == '[') {
                // the difference, measured in milliseconds, between the current time and midnight, January 1, 1970 UTC.
                timeStamp = System.currentTimeMillis();
                begin = s;
                break;
            }
        }
        // If no beginning then consume it all with no publish.
        if (begin == -1) {
            System.out.println("B) No begin - consume garbage");
            return len;
        }
        // Find end of message
        int end = -1;
        for (int s = begin + 1; s < len; s++) {
            if (buffer[s] == ']') {
                end = s;
                break;
            }
        }
        // If no end then consume nothing. Data will be appended and received again.
        if (end == -1) {
            System.out.println("B) No end - accume to end");
            return 0;
        }
        // We have a begin and an end - strip off [].
        final int finalBegin = begin + 1;
        final int finalEnd = end - 1;
        final short messageLen = (short)(finalEnd - finalBegin + 1);
        if (messageLen > 0) {
            channel.publishTopic(publishTopic, pubSubWriter -> {
                pubSubWriter.writeLong(timeStamp);
                pubSubWriter.writeShort(messageLen);
                System.out.println(String.format("B) %d:'%s'", messageLen, new String(buffer, finalBegin, messageLen)));
                pubSubWriter.write(buffer, finalBegin, messageLen);
            });
        }
        else {
            System.out.println("B) Empty Message");
        }
        // Consume only to the end. Our next message should begin with a [.
        return end + 1;
    }
}
