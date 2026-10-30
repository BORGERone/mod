package net.minecraft.network.chat;public interface Component {static MutableComponent translatable(String s){return new MutableComponent(s);}String getString();}
