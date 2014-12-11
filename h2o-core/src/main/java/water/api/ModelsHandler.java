package water.api;

import hex.Model;
import water.*;
import water.api.FramesHandler.Frames;
import water.fvec.Frame;

import java.util.*;

class ModelsHandler<I extends ModelsHandler.Models, S extends ModelsBase<I, S>> extends Handler {
  @Override protected int min_ver() { return 2; }
  @Override protected int max_ver() { return Integer.MAX_VALUE; }

  /** Class which contains the internal representation of the models list and params. */
  protected static final class Models extends Iced {
    public Key key;
    public Model[] models;
    public boolean find_compatible_frames = false;

    public static Model[] fetchAll() {
      final Key[] modelKeys = KeySnapshot.globalSnapshot().filter(new KeySnapshot.KVFilter() {
        @Override
        public boolean filter(KeySnapshot.KeyInfo k) {
          return Value.isSubclassOf(k._type, Model.class);
        }
      }).keys();

      Model[] models = new Model[modelKeys.length];
      for (int i = 0; i < modelKeys.length; i++) {
        Model model = getFromDKV(modelKeys[i]);
        models[i] = model;
      }

      return models;
    }

    /**
     * Fetch all the Frames so we can see if they are compatible with our Model(s).
     */
    protected Map<Frame, Set<String>> fetchFrameCols() {
      Frame[] all_frames = null;
      Map<Frame, Set<String>> all_frames_cols = null;

      if (this.find_compatible_frames) {
        // caches for this request
        all_frames = Frames.fetchAll();
        all_frames_cols = new HashMap<Frame, Set<String>>();

        for (Frame f : all_frames) {
          all_frames_cols.put(f, new HashSet<String>(Arrays.asList(f._names)));
        }
      }
      return all_frames_cols;
    }

    /**
     * For a given model return an array of the compatible frames.
     *
     * @param model The model to fetch the compatible frames for.
     * @param all_frames An array of all the Frames in the DKV.
     * @param all_frames_cols A Map of Frame to a Set of its column names.
     * @return
     */
    private static Frame[] findCompatibleFrames(Model model, Frame[] all_frames, Map<Frame, Set<String>> all_frames_cols) {
      List<Frame> compatible_frames = new ArrayList<Frame>();

      Set<String> model_column_names = new HashSet(Arrays.asList(model._output._names));

      for (Map.Entry<Frame, Set<String>> entry : all_frames_cols.entrySet()) {
        Frame frame = entry.getKey();
        Set<String> frame_cols = entry.getValue();

        if (frame_cols.containsAll(model_column_names)) {
          // See if adapt throws an exception or not.
          try {
            if( model.adaptTestForTrain(new Frame(frame), false).length == 0 )
              compatible_frames.add(frame);
          } catch( IllegalArgumentException e ) {
            // skip
          }
        }
      }
      return compatible_frames.toArray(new Frame[0]);
    }
  }

  /** Return all the models. */
  @SuppressWarnings("unused") // called through reflection by RequestServer
  public Schema list(int version, ModelsV3 s) {
    Models m = s.createAndFillImpl();
    m.models = Models.fetchAll();
    return s.fillFromImpl(m);
  }

  // TODO: almost identical to ModelsHandler; refactor
  public static Model getFromDKV(String key_str) {
    return getFromDKV(Key.make(key_str));
  }

  // TODO: almost identical to ModelsHandler; refactor
  public static Model getFromDKV(Key key) {
    if (null == key)
      throw new IllegalArgumentException("Got null key.");

    Value v = DKV.get(key);
    if (null == v)
      throw new IllegalArgumentException("Did not find key: " + key.toString());

    Iced ice = v.get();
    if (! (ice instanceof Model))
      throw new IllegalArgumentException("Expected a Model for key: " + key.toString() + "; got a: " + ice.getClass());

    return (Model)ice;
  }

  /** Return a single model. */
  @SuppressWarnings("unused") // called through reflection by RequestServer
  public Schema fetch(int version, ModelsV3 s) {
    Model model = getFromDKV(s.key.key());
    s.models = new ModelSchema[1];
    s.models[0] = (ModelSchema)Schema.schema(version, model).fillFromImpl(model);

    if (s.find_compatible_frames) {
      // TODO: refactor fetchFrameCols so we don't need this Models object
      Models m = new Models();
      m.models = new Model[1];
      m.models[0] = model;
      m.find_compatible_frames = true;
      Frame[] compatible = Models.findCompatibleFrames(model, Frames.fetchAll(), m.fetchFrameCols());
      s.compatible_frames = new FrameV2[compatible.length]; // TODO: FrameBase
      s.models[0].compatible_frames = new String[compatible.length];
      int i = 0;
      for (Frame f : compatible) {
        s.compatible_frames[i] = new FrameV2(f).fillFromImpl(f); // TODO: FrameBase
        s.models[0].compatible_frames[i] = f._key.toString();
        i++;
      }
    }

    return s;
  }

  /** Remove an unlocked model.  Fails if model is in-use. */
  @SuppressWarnings("unused") // called through reflection by RequestServer
  public Schema delete(int version, ModelsV3 s) {
    Model model = getFromDKV(s.key.key());
    if (null == model)
      throw new IllegalArgumentException("Model key not found: " + s.key);
    model.delete();             // lock & remove
    // TODO: Hm, which Schema should we use here?  Surely not a hardwired InspectV1. . .
    InspectV1 i = new InspectV1();
    i.key = s.key;
    return s;
  }

  /**
   * Remove ALL an unlocked models.  Throws IAE for all deletes that failed
   * (perhaps because the Models were locked & in-use).
   */
  @SuppressWarnings("unused") // called through reflection by RequestServer
  public Schema deleteAll(int version, ModelsV3 models) {
    final Key[] modelKeys = KeySnapshot.globalSnapshot().filter(new KeySnapshot.KVFilter() {
        @Override public boolean filter(KeySnapshot.KeyInfo k) {
          return Value.isSubclassOf(k._type, Model.class);
        }
      }).keys();

    String err=null;
    Futures fs = new Futures();
    for( int i = 0; i < modelKeys.length; i++ ) {
      try {
        getFromDKV(modelKeys[i]).delete(null,fs);
      } catch( IllegalArgumentException iae ) {
        err += iae.getMessage();
      }
    }
    fs.blockForPending();
    if( err != null ) throw new IllegalArgumentException(err);

    // TODO: Hm, which Schema should we use here?  Surely not a hardwired InspectV1. . .
    InspectV1 s = new InspectV1();
    return s;
  }
}
