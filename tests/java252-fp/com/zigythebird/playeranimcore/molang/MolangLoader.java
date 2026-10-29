package com.zigythebird.playeranimcore.molang;
import java.util.*;
import com.google.gson.JsonElement;
import com.zigythebird.playeranimcore.animation.AnimationController;
import team.unnamed.mocha.MochaEngine;
import team.unnamed.mocha.parser.MolangParser;
import team.unnamed.mocha.parser.ast.Expression;
import team.unnamed.mocha.parser.ast.FloatExpression;
import team.unnamed.mocha.runtime.IsConstantExpression;
public final class MolangLoader {
 public static final MochaEngine<?> MOCHA_ENGINE=MochaEngine.createStandard();
 public static boolean isConstant(List<Expression> expressions){return expressions.stream().anyMatch(IsConstantExpression::test);}
 public static MochaEngine<AnimationController> createNewEngine(AnimationController c){return MochaEngine.createStandard(c);}
 public static List<Expression> parseJson(boolean toRad,JsonElement json,Expression fallback){
  return parseJson(toRad,json,Collections.singletonList(fallback));
 }
 public static List<Expression> parseJson(boolean toRad,JsonElement json,List<Expression> fallback){
  if(json==null)return fallback;
  try(MolangParser p=MolangParser.parser(json.getAsString())){
   List<Expression> ex=p.parseAll();
   if(ex.size()==1 && toRad && IsConstantExpression.test(ex.getFirst())){
    List<Expression> out=new ArrayList<>();
    out.add(FloatExpression.of(Math.toRadians(MOCHA_ENGINE.eval(ex))));
    return out;
   }
   return ex;
  }catch(Exception e){return fallback;}
 }
}
