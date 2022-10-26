package ok.dht.test.monakhov;


import com.google.common.primitives.SignedBytes;
import one.nio.http.Request;

import java.io.Serializable;
import java.sql.Timestamp;

public class EntryWrapper implements Comparable<EntryWrapper>, Serializable {
    final byte[] bytes;
    final Timestamp timestamp;
    final boolean isTombstone;

    public EntryWrapper(Request request, Timestamp timestamp) {
        this.bytes = request.getBody();
        this.isTombstone = request.getMethod() == Request.METHOD_DELETE;
        this.timestamp = timestamp;
    }

    public EntryWrapper(byte[] bytes, Timestamp timestamp, boolean isTombstone) {
        this.bytes = bytes;
        this.timestamp = timestamp;
        this.isTombstone = isTombstone;
    }

    @Override
    public int compareTo(EntryWrapper o) {
        int c = timestamp.compareTo(o.timestamp);
        if (c == 0) {
            if (isTombstone && o.isTombstone) {
                return 0;
            }
            if (isTombstone) {
                return 1;
            }
            if (o.isTombstone) {
                return  -1;
            }
            return SignedBytes.lexicographicalComparator().compare(bytes, o.bytes);
        }
        return c;
    }
}
