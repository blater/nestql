package blater.nestql.parser;

import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;

/*
 * Responsibility: Converts ANTLR syntax callbacks into immediate
 * parser exceptions.
 */
final class SyntaxErrorListener extends BaseErrorListener {
  @Override
  public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol,
                          int line, int charPositionInLine, String msg, RecognitionException e)
  {
    throw new HiqlSyntaxException("line " + line + ":" + charPositionInLine + " " + msg);
  }
}
