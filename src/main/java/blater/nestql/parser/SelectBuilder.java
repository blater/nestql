package blater.nestql.parser;

import blater.nestql.core.parser.HiQLLexer;
import blater.nestql.core.parser.HiQLParser;
import blater.nestql.domain.HierarchyPath;
import blater.nestql.parser.script.NestStatement;
import blater.nestql.domain.KeyedPath;
import blater.nestql.domain.CorrelationRule;
import blater.nestql.domain.MappingCondition;
import blater.nestql.domain.MappingPlan;
import blater.nestql.domain.OutputField;
import blater.nestql.util.Log;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.misc.Interval;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static blater.nestql.util.ValueUtil.hasValue;

/*
 * Responsibility: Builds parsed SELECT statements, including query
 * SQL text, hierarchy mappings, property capture, and XML hints.
 */
final class SelectBuilder {

  private static final String SELECT_BRANCH_COLUMN = "hiql_select_branch";
  private static final String SELECT_BRANCH_VALUE_PREFIX = "select_branch_";

  private SelectBuilder() {
  }

  static NestStatement buildSelect(HiQLParser.SelectStatementContext ctx)
  {
    if (! validateUsingPlacement(ctx))
      Log.fatal(HiqlSyntaxException.class, "using metadata is only valid on the first hierarchy union branch.");

    UsingInfo using = readUsing(ctx.selectBranch(0).usingClause());
    List<Branch> branches = readBranches(ctx);
    validateBranchRoots(branches);
    boolean hasHierarchyFields = branches.stream().anyMatch(Branch::hasHierarchyFields);

    if (!hasHierarchyFields && using.hasValues()) {
      Log.fatal(HiqlSyntaxException.class, "using metadata requires at least one hierarchy mapping alias.");
    }


    if (!hasHierarchyFields) {
      return NestStatement.select(
          ParseUtils.textOf(ctx).trim(),
          new MappingPlan(),
          using.namespace
      );
    }

    List<OrderItem> orderItems = readOrderItems(ctx.orderByClause());
    List<StructureItem> structureItems = readStructureItems(ctx.structureClause(), branches);
    structureItems.addAll(readLegacyStructureItems(orderItems, structureItems));
    List<InternalExpression> internalExpressions = internalExpressions(structureItems, orderItems);

    for (Branch branch : branches) {
      for (InternalExpression internal : internalExpressions) {
        boolean mapsKeyedPath = internal.path == null || branch.mapsPath(internal.path);
        branch.internalItems.add(mapsKeyedPath ? branch.internalItemText(internal.expression) : "null");
      }
    }

    List<Integer> nonKeyCounts = new ArrayList<>();
    for (Branch branch : branches) {
      nonKeyCounts.add(branch.emittedNonKeyColumnCount());
    }
    StringBuilder sql = new StringBuilder();
    List<OutputField> fields = new ArrayList<>();
    for (int idx = 0; idx < branches.size(); idx++) {
      if (idx > 0) sql.append(" union all ");
      emitBranchSql(sql, fields, branches.get(idx), idx, nonKeyCounts);
    }
    sql.append(genericOrderBy(orderItems, internalExpressions));

    Map<String, String> internalColumns = new LinkedHashMap<>();
    for (int idx = 0; idx < internalExpressions.size(); idx++) {
      internalColumns.put(internalExpressions.get(idx).expression, "col" + (idx + 1));
    }
    List<KeyedPath> keyedPaths = new ArrayList<>();
    for (StructureItem item : structureItems) {
      List<String> columns = item.keyExpressions.stream().map(internalColumns::get).toList();
      keyedPaths.add(new KeyedPath(item.path, columns));
    }

    MappingPlan plan = new MappingPlan(fields, legacyCorrelationRules(orderItems, internalExpressions), keyedPaths);
    return NestStatement.select(sql.toString(), plan, using.namespace);
  }


  private static boolean validateUsingPlacement(HiQLParser.SelectStatementContext ctx) {
    boolean isValid = true;

    for (int idx = 1; idx < ctx.selectBranch().size(); idx++) {
      if (ctx.selectBranch(idx).usingClause() != null) {
        isValid = false;
        break;
      }
    }
    return isValid;
  }

  private static void validateBranchRoots(List<Branch> branches) {
    String root = null;
    for (Branch branch : branches) {
      for (SelectItem item : branch.items) {
        if (item.outputPath == null)
          continue;
        String itemRoot = item.outputPath.getRootName();
        if (root == null) {
          root = itemRoot;
        } else if (!root.equals(itemRoot)) {
          Log.fatal(HiqlSyntaxException.class, "hierarchy union branches must share one document wrapper.");
        }
      }
    }
  }

  private static UsingInfo readUsing(HiQLParser.UsingClauseContext ctx) {
    UsingInfo info = new UsingInfo();
    if (ctx == null) return info;
    for (HiQLParser.UsingItemContext item : ctx.usingItem()) {
      if (item.K_SCHEMA() != null) {
        info.schemaOrRootPresent = true;
      } else if (item.K_NAMESPACE() != null) {
        if (item.STRING() != null) {
          info.namespace = ParseUtils.unquoteString(item.STRING().getText());
        } else {
          info.namespace = ParseUtils.unquoteIdentifier(item.QUOTED_IDENTIFIER().getText());
        }
      } else if (item.K_XMLROOT() != null) {
        info.schemaOrRootPresent = true;
      }
    }
    return info;
  }

  private static List<Branch> readBranches(HiQLParser.SelectStatementContext ctx) {
    List<Branch> branches = new ArrayList<>();
    for (HiQLParser.SelectBranchContext branchCtx : ctx.selectBranch()) {
      Branch branch = new Branch();
      for (HiQLParser.SelectItemContext itemCtx : branchCtx.selectItem()) {
        SelectItem item = readSelectItem(itemCtx);
        branch.items.add(item);
      }
      if (branchCtx.sqlTail() != null) {
        branch.sqlTail = ParseUtils.textOf(branchCtx.sqlTail());
      }
      branches.add(branch);
    }
    return branches;
  }

  private static SelectItem readSelectItem(HiQLParser.SelectItemContext ctx) {
    SelectItem selectItem = null;
    List<Token> exprTokens = tokensIn(ctx.selectExpr());
    String exprText = joinText(exprTokens);
    String alias = ctx.sqlAlias() == null ? null : ParseUtils.unquoteIdentifier(ctx.sqlAlias().name().getText());

    String name = alias != null ? alias : trailingIdentifier(exprTokens);
    if (ctx.mappingAlias() != null) {
      String cmd = null;
      String cmdArg = null;
      HiQLParser.MappingAliasContext mapping = ctx.mappingAlias();
      var path = PathContextMapper.toHierarchyPath(mapping.path());
      var absentOnNull = mapping.nullOutputPolicy() != null;
      if (hasValue(mapping.name())) {
        cmd = mapping.name(0).getText();
        cmdArg = mapping.name().size() > 1 ? mapping.name(1).getText() : null;
      }
      selectItem = new SelectItem(exprText, name, path, absentOnNull, cmd, cmdArg);
    } else {
      selectItem = new SelectItem(exprText, name);
    }

    return selectItem;
  }

  private static List<OrderItem> readOrderItems(HiQLParser.OrderByClauseContext ctx) {
    List<OrderItem> items = new ArrayList<>();
    if (ctx == null)
      return items;

    for (HiQLParser.OrderItemContext itemCtx : ctx.orderItem()) {
      OrderItem orderBy = new OrderItem();
      orderBy.expression = ParseUtils.textOf(itemCtx.orderExpr()).trim();
      if (itemCtx.K_DESC() != null) {
        orderBy.direction = "desc";
      } else if (itemCtx.K_ASC() != null) {
        orderBy.direction = "asc";
      }
      for (HiQLParser.CreatesNewClauseContext createsNew : itemCtx.createsNewClause()) {
        orderBy.legacyPaths.add(PathContextMapper.toHierarchyPath(createsNew.path()));
      }
      items.add(orderBy);
    }
    return items;
  }

  private static void emitBranchSql(
      StringBuilder sql,
      List<OutputField> fields,
      Branch branch,
      int branchIndex,
      List<Integer> nonKeyCounts) {
    String branchValue = SELECT_BRANCH_VALUE_PREFIX + branchIndex;
    StringBuilder selectItems = new StringBuilder();
    int rsColNum = 1;
    int columnNum = 0;

    for (String internalItem : branch.internalItems) {
      appendItem(selectItems, rsColNum, internalItem);
      rsColNum++;
    }

    for (int otherIdx = 0; otherIdx < nonKeyCounts.size(); otherIdx++) {
      if (otherIdx == branchIndex) {
        while (columnNum < branch.items.size()) {
          SelectItem item = branch.items.get(columnNum++);
          appendItem(selectItems, rsColNum, item.text);
          recordOutputField(fields, branchValue, item, rsColNum);
          rsColNum++;
        }
      } else {
        int fillCount = nonKeyCounts.get(otherIdx);
        for (int j = 0; j < fillCount; j++) {
          appendItem(selectItems, rsColNum, "null");
          rsColNum++;
        }
      }
    }

    selectItems.append(", '").append(branchValue).append("' as ").append(quoteAlias(SELECT_BRANCH_COLUMN));

    sql.append("select ").append(selectItems);
    if (branch.sqlTail != null && !branch.sqlTail.isEmpty()) {
      sql.append(' ').append(branch.sqlTail);
    }
  }

  private static void appendItem(StringBuilder buf, int rsColNum, String text) {
    if (rsColNum > 1) buf.append(", ");
    buf.append(text).append(" as ").append(quoteAlias("col" + rsColNum));
  }

  private static void recordOutputField(List<OutputField> fields, String branchValue, SelectItem item, int rsColNum)
  {
    if (item.outputPath == null)
      return;

    String column = "col" + rsColNum;
    List<MappingCondition> conditions = new ArrayList<>();
    conditions.add(MappingCondition.eq(SELECT_BRANCH_COLUMN, branchValue));
    fields.add(new OutputField(item.outputPath, column, item.appendText, conditions, item.absentOnNull));
  }

  private static String genericOrderBy(List<OrderItem> orderItems, List<InternalExpression> internalExpressions) {
    if (orderItems.isEmpty())
      return "";

    StringBuilder buf = new StringBuilder(" order by ");
    for (int idx = 0; idx < orderItems.size(); idx++) {
      if (idx > 0)
        buf.append(", ");
      OrderItem item = orderItems.get(idx);
      int expressionIndex = indexOfExpression(internalExpressions, item.expression);
      buf.append(quoteAlias("col" + (expressionIndex + 1)));
      String direction = item.direction;
      if (direction != null)
        buf.append(' ').append(direction);
    }
    return buf.toString();
  }

  private static int indexOfExpression(List<InternalExpression> expressions, String expression) {
    for (int idx = 0; idx < expressions.size(); idx++) {
      if (expressions.get(idx).expression.equals(expression))
        return idx;
    }
    throw new IllegalStateException("Missing internal expression: " + expression);
  }

  private static List<InternalExpression> internalExpressions(
      List<StructureItem> structureItems, List<OrderItem> orderItems) {
    Map<String, InternalExpression> expressions = new LinkedHashMap<>();
    for (StructureItem item : structureItems) {
      for (String expression : item.keyExpressions) {
        expressions.putIfAbsent(expression, new InternalExpression(expression, item.path));
      }
    }
    for (OrderItem item : orderItems) {
      expressions.putIfAbsent(item.expression, new InternalExpression(item.expression, null));
    }
    return new ArrayList<>(expressions.values());
  }

  private static List<StructureItem> readStructureItems(
      HiQLParser.StructureClauseContext ctx, List<Branch> branches) {
    List<StructureItem> items = new ArrayList<>();
    if (ctx == null)
      return items;

    for (HiQLParser.StructureItemContext itemCtx : ctx.structureItem()) {
      HierarchyPath path = PathContextMapper.toHierarchyPath(itemCtx.path());
      if (path.isAttribute())
        Log.fatal(HiqlSyntaxException.class, "structure keys must target object paths, not attributes: " + path);
      if (items.stream().anyMatch(item -> item.path.equals(path)))
        Log.fatal(HiqlSyntaxException.class, "duplicate structure path: " + path);
      if (items.stream().anyMatch(item -> path.isBelow(item.path) == false && item.path.isBelow(path)))
        Log.fatal(HiqlSyntaxException.class, "structure paths must be declared parent before child: " + path);
      if (branches.stream().anyMatch(branch -> branch.mapsExactPath(path)))
        Log.fatal(HiqlSyntaxException.class, "structure keys must target object paths, not mapped values: " + path);

      List<String> expressions = itemCtx.structureKeyExpr().stream()
          .map(ParseUtils::textOf)
          .map(String::trim)
          .toList();
      items.add(new StructureItem(path, expressions));
    }
    return items;
  }

  private static List<StructureItem> readLegacyStructureItems(
      List<OrderItem> orderItems, List<StructureItem> explicitItems) {
    List<StructureItem> items = new ArrayList<>();
    for (OrderItem orderItem : orderItems) {
      for (HierarchyPath path : orderItem.legacyPaths) {
        boolean duplicate = explicitItems.stream().anyMatch(item -> item.path.equals(path))
            || items.stream().anyMatch(item -> item.path.equals(path));
        if (duplicate)
          Log.fatal(HiqlSyntaxException.class, "duplicate structure path: " + path);
        items.add(new StructureItem(path, List.of(orderItem.expression)));
      }
    }
    return items;
  }

  private static List<CorrelationRule> legacyCorrelationRules(
      List<OrderItem> orderItems, List<InternalExpression> internalExpressions) {
    List<CorrelationRule> rules = new ArrayList<>();
    for (OrderItem item : orderItems) {
      for (HierarchyPath path : item.legacyPaths) {
        String column = "col" + (indexOfExpression(internalExpressions, item.expression) + 1);
        rules.add(new CorrelationRule(path, List.of(MappingCondition.newValue(column))));
      }
    }
    return rules;
  }

  private static String quoteAlias(String alias) {
    return "\"" + alias + "\"";
  }

  private static List<Token> tokensIn(ParserRuleContext ctx) {
    List<Token> result = new ArrayList<>();
    if (ctx != null)
      collectLeafTokens(ctx, result);
    return result;
  }

  private static void collectLeafTokens(ParseTree node, List<Token> out) {
    if (node instanceof TerminalNode terminal) {
      Token token = terminal.getSymbol();
      if (token.getType() != Token.EOF)
        out.add(token);
      return;
    }
    for (int idx = 0; idx < node.getChildCount(); idx++) {
      collectLeafTokens(node.getChild(idx), out);
    }
  }

  private static String joinText(List<Token> tokens) {
    if (tokens.isEmpty())
      return "";

    Token first = tokens.getFirst();
    Token last = tokens.getLast();
    return first.getInputStream().getText(new Interval(first.getStartIndex(), last.getStopIndex()));
  }

  private static String trailingIdentifier(List<Token> tokens) {
    for (int idx = tokens.size() - 1; idx >= 0; idx--) {
      Token token = tokens.get(idx);
      if (token.getType() == HiQLLexer.IDENT
          || token.getType() == HiQLLexer.QUOTED_IDENTIFIER) {
        return ParseUtils.unquoteIdentifier(token.getText());
      }
    }
    return null;
  }

  /*
   * Responsibility: Carries optional select using metadata while a
   * SELECT statement is being built.
   */
  private static final class UsingInfo {
    boolean schemaOrRootPresent;
    String namespace;

    boolean hasValues() {
      return schemaOrRootPresent || namespace != null;
    }
  }

  /*
   * Responsibility: Holds one select branch while SQL text and
   * hierarchy mapping metadata are being assembled.
   */
  private static final class Branch {
    final List<SelectItem> items = new ArrayList<>();
    final List<String> internalItems = new ArrayList<>();
    String sqlTail;

    int emittedNonKeyColumnCount() {
      return items.size();
    }

    boolean hasHierarchyFields() {
      return items.stream().anyMatch(item -> item.outputPath != null);
    }

    boolean mapsPath(HierarchyPath path) {
      return items.stream().anyMatch(item -> item.outputPath != null
          && (item.outputPath.equals(path) || item.outputPath.isBelow(path)));
    }

    boolean mapsExactPath(HierarchyPath path) {
      return items.stream().anyMatch(item -> path.equals(item.outputPath));
    }

    String internalItemText(String expression) {
      for (SelectItem item : items) {
        if (expression.equalsIgnoreCase(item.name) || expression.equalsIgnoreCase(item.text))
          return item.text;
      }
      return expression;
    }
  }

  /*
   * Responsibility: Holds one SELECT item and any hierarchy mapping
   * attached to it during statement building.
   */
  private static final class SelectItem {
    final String text;
    final String name;
    HierarchyPath outputPath;
    String appendText = null;
    boolean absentOnNull;

    SelectItem(String text, String name) {
      this.text = text;
      this.name = name;
      this.outputPath = null;
    }

    SelectItem(String text, String name, HierarchyPath path, boolean absentOnNull, String cmd, String cmdArg) {
      this.text = text;
      this.name = name;
      this.outputPath = path;
      this.absentOnNull = absentOnNull;
      this.appendText = parseAppendCommand(cmd, cmdArg);
    }

    private static String parseAppendCommand(String flagName, String arg) {
      if (flagName == null)
        return null;
      if (flagName.equals("append") && arg != null) {
        return switch (arg.toLowerCase()) {
          case "space" -> " ";
          case "dash" -> "-";
          case "comma" -> ",";
          case "newline" -> "\n";
          default -> null;
        };
      }
      return null;
    }

  }

  private static final class OrderItem {
    String expression;
    String direction;
    final List<HierarchyPath> legacyPaths = new ArrayList<>();
  }

  private record StructureItem(HierarchyPath path, List<String> keyExpressions) {
  }

  private record InternalExpression(String expression, HierarchyPath path) {
  }
}
