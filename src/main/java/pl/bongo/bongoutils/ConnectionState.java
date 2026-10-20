package pl.bongo.bongoutils;

/** State belongs to the actual encrypted connection, never to client-provided UUIDs. */
public interface ConnectionState {
    default String bongo$language() { return "en_us"; }
    default void bongo$language(String language) {}
    boolean bongo$verified();
    void bongo$verified(boolean value);
    boolean bongo$authenticated();
    void bongo$authenticated(boolean value);
    boolean bongo$skinRefresh();
    void bongo$skinRefresh(boolean value);
}
