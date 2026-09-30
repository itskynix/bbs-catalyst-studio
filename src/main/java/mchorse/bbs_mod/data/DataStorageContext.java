package mchorse.bbs_mod.data;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class DataStorageContext
{
    public final DataInputStream in;
    public final DataOutputStream out;

    private Map<String, Integer> keyMap;
    private Map<Integer, String> intMap;
    private int index;
    private KeyType type = KeyType.BYTE;

    /* Length-prefixed arrays/collections in this binary format come straight off the wire (or a
     * possibly-corrupted file) with no size validation of their own. Without this guard, a single
     * bad 4-byte length prefix well under any whole-packet size cap can still make
     * ByteArrayType/IntArrayType/LongArrayType/ShortArrayType.read() try to allocate gigabytes in
     * one shot - an instant OutOfMemoryError (or, for a negative/overflowed length, a
     * NegativeArraySizeException) that takes down the client or the dedicated server.
     * 16,000,000 elements comfortably covers any legitimate array this mod writes. */
    public static final int MAX_ARRAY_LENGTH = 16_000_000;

    public static void checkArrayLength(int length) throws IOException
    {
        if (length < 0 || length > MAX_ARRAY_LENGTH)
        {
            throw new IOException("Refusing to read an array/collection of " + length + " elements (corrupt or malicious length prefix)");
        }
    }

    /* Guards against a deeply (or infinitely, if malformed) nested map/list payload blowing the
     * Java call stack with a StackOverflowError, which - unlike IOException - none of the packet
     * handlers' try/catch(Exception) blocks can catch. */
    public static final int MAX_NESTING_DEPTH = 64;

    private int nestingDepth;

    public void enterNesting() throws IOException
    {
        this.nestingDepth += 1;

        if (this.nestingDepth > MAX_NESTING_DEPTH)
        {
            throw new IOException("Data structure is nested too deeply (corrupt or malicious payload)");
        }
    }

    public void exitNesting()
    {
        this.nestingDepth -= 1;
    }

    public DataStorageContext(DataInputStream in)
    {
        this.in = in;
        this.out = null;
    }

    public DataStorageContext(DataOutputStream out)
    {
        this.in = null;
        this.out = out;
    }

    public String getKey(int index)
    {
        return this.intMap == null ? null : this.intMap.get(index);
    }

    public int getIndex(String key)
    {
        return this.keyMap == null ? -1 : this.keyMap.get(key);
    }

    public void put(String key)
    {
        if (this.keyMap == null)
        {
            this.keyMap = new HashMap<>();
        }

        if (!this.keyMap.containsKey(key))
        {
            this.keyMap.put(key, this.index);
            this.index += 1;
        }
    }

    public void read() throws IOException
    {
        this.intMap = new HashMap<>();
        this.type = KeyType.from(this.in.readByte());

        int c = this.type.read(this.in);

        checkArrayLength(c);

        for (int i = 0; i < c; i++)
        {
            this.intMap.put(this.type.read(this.in), this.in.readUTF());
        }
    }

    public String readKey() throws IOException
    {
        return this.getKey(this.type.read(this.in));
    }

    public void write() throws IOException
    {
        if (this.keyMap == null)
        {
            this.keyMap = new HashMap<>();
        }

        if (this.keyMap.size() < 256)
        {
            this.type = KeyType.BYTE;
        }
        else if (this.keyMap.size() < 65536)
        {
            this.type = KeyType.SHORT;
        }
        else
        {
            this.type = KeyType.INT;
        }

        this.out.writeByte(this.type.type);
        this.type.write(this.out, this.keyMap.size());

        for (Map.Entry<String, Integer> entry : this.keyMap.entrySet())
        {
            this.type.write(this.out, entry.getValue());
            this.out.writeUTF(entry.getKey());
        }
    }

    public void writeIndex(String key) throws IOException
    {
        this.type.write(this.out, this.getIndex(key));
    }
}