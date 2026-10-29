package traben.tconfig;
public class TConfigHandler<T extends TConfig> {
 private final T cfg;
 public TConfigHandler(T cfg){this.cfg=cfg;}
 public T getConfig(){return cfg;}
}
