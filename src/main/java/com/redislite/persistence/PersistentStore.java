package com.redislite.persistence;
import com.redislite.store.*;
import java.util.Optional;

public final class PersistentStore implements KeyValueStore {
    private final KeyValueStore inner; private final MutationLog log;
    public PersistentStore(KeyValueStore inner, MutationLog log){this.inner=inner;this.log=log;
        if (inner instanceof ExpiringLruStore s) s.setEvictionListener(log::appendDelete);
    }
    private void check(){if(!log.isHealthy())throw new PersistenceException("AOF is unhealthy");}
    @Override public void set(String k,String v){check();inner.set(k,v);log.appendSet(k,v);}
    @Override public Optional<String> get(String k){return inner.get(k);}
    @Override public boolean delete(String k){check();boolean x=inner.delete(k);if(x)log.appendDelete(k);return x;}
    @Override public boolean exists(String k){return inner.exists(k);}
    @Override public int size(){return inner.size();}
    @Override public boolean setExpiryAt(String k,long at){check();boolean x=inner.setExpiryAt(k,at);if(x)log.appendExpireAt(k,at);return x;}
    @Override public long ttlMillis(String k){return inner.ttlMillis(k);}
}
