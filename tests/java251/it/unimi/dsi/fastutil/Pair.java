package it.unimi.dsi.fastutil;public interface Pair<L,R> {L left();R right();static <L,R> Pair<L,R> of(L l,R r){return new Pair<L,R>(){public L left(){return l;}public R right(){return r;}};}}
