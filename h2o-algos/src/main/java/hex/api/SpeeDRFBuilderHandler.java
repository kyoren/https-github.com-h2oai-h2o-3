package hex.api;

import hex.schemas.SpeeDRFV2;
import hex.singlenoderf.SpeeDRF;
import water.api.ModelBuilderHandler;
import water.api.Schema;

public class SpeeDRFBuilderHandler extends ModelBuilderHandler<SpeeDRF, SpeeDRFV2, SpeeDRFV2.SpeeDRFParametersV2> {
  /** Required so that Handler.handle() gets the correct schema types. */
  @SuppressWarnings("unused") // called through reflection by RequestServer
  public Schema train(int version, SpeeDRFV2 builderSchema) {
    return super.do_train(version, builderSchema);
  }

  /** Required so that Handler.handle() gets the correct schema types. */
  @SuppressWarnings("unused") // called through reflection by RequestServer
  public SpeeDRFV2 validate_parameters(int version, SpeeDRFV2 builderSchema) {
    return super.do_validate_parameters(version, builderSchema);
  }
}

