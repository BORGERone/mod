package com.zigythebird.playeranimcore.molang;
import com.zigythebird.playeranimcore.animation.AnimationController;
import team.unnamed.mocha.MochaEngine;
import team.unnamed.mocha.parser.ast.Expression;
import team.unnamed.mocha.runtime.IsConstantExpression;
import java.util.List;
/** Test shim for PAL's platform event registration; keyframe math uses real PAL and Mocha. */
public final class MolangLoader {
 public static final MochaEngine<?> MOCHA_ENGINE=MochaEngine.createStandard();
 public static boolean isConstant(List<Expression> expressions){return expressions.stream().anyMatch(IsConstantExpression::test);}
 public static MochaEngine<AnimationController> createNewEngine(AnimationController c){return MochaEngine.createStandard(c);}
}
