package hex.schemas;

import hex.singlenoderf.SpeeDRFModel;
import water.api.*;

public class SpeeDRFModelV2 extends ModelSchema<SpeeDRFModel, SpeeDRFModelV2, SpeeDRFModel.SpeeDRFParameters, SpeeDRFV2.SpeeDRFParametersV2, SpeeDRFModel.SpeeDRFModelOutput, SpeeDRFModelV2.SpeeDRFModelOutputV2> {

  public static final class SpeeDRFModelOutputV2 extends ModelOutputSchema<SpeeDRFModel.SpeeDRFModelOutput, SpeeDRFModelOutputV2> {
  } // SpeeDRFModelOutputV2

  // TODO: I think we can implement the following two in ModelSchema, using reflection on the type parameters.
  public SpeeDRFV2.SpeeDRFParametersV2 createParametersSchema() { return new SpeeDRFV2.SpeeDRFParametersV2(); }
  public SpeeDRFModelOutputV2 createOutputSchema() { return new SpeeDRFModelOutputV2(); }
}
