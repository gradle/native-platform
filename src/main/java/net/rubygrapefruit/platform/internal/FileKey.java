package net.rubygrapefruit.platform.internal;

final class FileKey {
    private final int volumeId;
    private final long fileId;

    FileKey(int volumeId, long fileId) {
        this.volumeId = volumeId;
        this.fileId = fileId;
    }

    @Override
    public int hashCode() {
        return (int)(volumeId * 31 + fileId);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this)
            return true;
        if (!(obj instanceof FileKey))
            return false;

        FileKey other = (FileKey) obj;
        return (this.volumeId == other.volumeId) &&
                (this.fileId == other.fileId);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("(volumeId=")
                .append(Integer.toHexString(volumeId))
                .append(",fileId=")
                .append(Long.toHexString(fileId))
                .append(')');
        return sb.toString();
    }

}
