package traben.entity_model_features.models;
public final class EMFModel_ID {
 public final String id;public EMFModel_ID(String id){this.id=id;}
 @Override public boolean equals(Object o){return !testing.Probe250.wrongModelId && o instanceof EMFModel_ID m && id.equals(m.id);}
 @Override public int hashCode(){return id.hashCode();}
}
