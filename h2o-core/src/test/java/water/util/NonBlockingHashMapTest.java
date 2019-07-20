package water.util;

import org.junit.Test;
import water.nbhm.NonBlockingHashMap;

public class NonBlockingHashMapTest {

  @Test
  public void testPutIfAbsent() throws Exception {
    NonBlockingHashMap<K, K> map = new NonBlockingHashMap<>();
    
    Putter[] putters = new Putter[4];
    
    for (int i = 0; i < putters.length; i++) {
      putters[i] = new Putter(map);
      putters[i].start();
    }

    for (Putter putter : putters) {
      putter.join();
    }
  }
 
  private static class Putter extends Thread {
    NonBlockingHashMap<K, K> map;

    public Putter(NonBlockingHashMap<K, K> map) {
      this.map = map;
    }

    @Override
    public void run() {
      for (int i = 0; i < 100000000; i++) {
        K k = new K(System.currentTimeMillis(), this);
        K old = map.putIfAbsent(k, k);
        if (old == null) {
          if (map.getk(k) != k) {
            throw new IllegalStateException();
          }
        }
      }
    }
  }
  
  private static class K {
    private final long k;
    private final Putter p;

    public K(long k, Putter p) {
      this.k = k;
      this.p = p;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      K k1 = (K) o;

      return k == k1.k;
    }

    @Override
    public int hashCode() {
      return (int) (k ^ (k >>> 32));
    }

    @Override
    public String toString() {
      return "K{" +
              "k=" + k +
              '}';
    }
  }
  
}
