package water.fvec;

import water.*;

// The empty-compression function, where data is in 'int's.
public class C4FChunk extends Chunk {
  C4FChunk( byte[] bs ) { _mem=bs; _start = -1; _len = _mem.length>>2; }
  @Override public long   get ( int    i ) { return (long)UDP.get4f(_mem,i<<2); }
  @Override public double getd( int    i ) { return       UDP.get4f(_mem,i<<2); }
  @Override void   append2 ( long l, int exp ) { throw H2O.fail(); }
  @Override public AutoBuffer write(AutoBuffer bb) { throw H2O.fail(); }
  @Override public C4FChunk read(AutoBuffer bb) {
    _mem = bb.bufClose();
    _start = -1;
    _len = _mem.length>>2;
    assert _mem.length == _len<<2;
    return this;
  }
}