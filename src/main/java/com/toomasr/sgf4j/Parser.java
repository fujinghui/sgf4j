package com.toomasr.sgf4j;

import java.util.HashSet;
import java.util.Set;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.toomasr.sgf4j.parser.Game;
import com.toomasr.sgf4j.parser.GameNode;

public class Parser {
  private static final Logger log = LoggerFactory.getLogger(Parser.class);
  private final String originalGame;

  // http://www.red-bean.com/sgf/properties.html
  private static final Set<String> generalProps = new HashSet<String>();

  static {
    // Application used to generate the SGF
    generalProps.add("AP");
    // Black's Rating
    generalProps.add("BR");
    // White's Rating
    generalProps.add("WR");
    // KOMI
    generalProps.add("KM");
    // Black Player
    generalProps.add("PB");
    // Black Player
    generalProps.add("PW");
    // Charset
    generalProps.add("CA");
    // File format
    generalProps.add("FF");
    // Game type - 1 means Go
    generalProps.add("GM");
    // Size of the board
    generalProps.add("SZ");
    // Annotator
    generalProps.add("AN");
    // Rules
    generalProps.add("RU");
    // Time limit in seconds
    generalProps.add("TM");
    // How overtime is handled
    generalProps.add("OT");
    // Date of the game
    generalProps.add("DT");
    // Place of the game
    generalProps.add("PC");
    // Result of the game
    generalProps.add("RE");
    // How to show comments
    generalProps.add("ST");
    // How to print move numbers
    generalProps.add("PM");
    // Some more printing magic
    generalProps.add("FG");
    // Name of the game
    generalProps.add("GN");
    // Black territory or area
    generalProps.add("TB");
    // White territory or area
    generalProps.add("TW");
    // Handicap stones
    generalProps.add("HA");
    // "AB": add black stones AB[point list]
    generalProps.add("AB");
    // "AW": add white stones AW[point list]
    generalProps.add("AW");
    // add empty = remove stones
    generalProps.add("AE");
    // PL tells whose turn it is to play.
    generalProps.add("PL");
    // KGSDE - kgs scoring - marks all prisoner stones
    // http://senseis.xmp.net/?CgobanProblemsAndSolutions
    generalProps.add("KGSDE");
    // KGS - score white
    generalProps.add("KGSSW");
    // KGS - score black
    generalProps.add("KGSSB");
  }

  private static final Set<String> nodeProps = new HashSet<String>();

  static {
    // Move for Black
    nodeProps.add("B");
    // Move for White
    nodeProps.add("W");
    // marks given points with circle
    nodeProps.add("CR");
    // marks given points with cross
    nodeProps.add("MA");
    // selected points
    nodeProps.add("SL");
    // labels on points
    nodeProps.add("LB");
    // marks given points with triangle
    nodeProps.add("TR");
    // Number of white stones to play in this byo-yomi period
    nodeProps.add("OW");
    // Number of black stones to play in this byo-yomi period
    nodeProps.add("OB");
    // time left for white
    nodeProps.add("WL");
    // time left for black
    nodeProps.add("BL");
    // Comment
    nodeProps.add("C");
    /*
     * List of points - http://www.red-bean.com/sgf/proplist_ff.html
     * Label the given points with uppercase letters. Not used in FF 3 and FF 4!
     * 
     * Replaced by LB which defines the letters also:
     * Example: L[fg][es][jk] -> LB[fg:A][es:B][jk:C]
     */
    nodeProps.add("L");
  }

  private Stack<GameNode> treeStack = new Stack<GameNode>();

  public Parser(String game) {
    originalGame = game;
  }

  public Game parse() {
    Game game = new Game();

    // the root node
    GameNode parentNode = null;
    // replace token delimiters

    int moveNo = 1;

    for (int i = 0; i < originalGame.length(); i++) {
      char chr = originalGame.charAt(i);
      if (';' == chr && (i == 0 || originalGame.charAt(i - 1) != '\\')) {
        String nodeContents = consumeUntil(originalGame, i);
        i = i + nodeContents.length();

        GameNode node = parseToken(nodeContents, parentNode, game);
        if (node.isMove()) {
          node.setMoveNo(moveNo++);
        }

        if (parentNode == null) {
          parentNode = node;
          game.setRootNode(parentNode);
        }
        else {
          parentNode.addChild(node);
          parentNode = node;
        }
      }
      else if ('(' == chr && parentNode != null) {
        treeStack.push(parentNode);
      }
      else if (')' == chr) {
        if (treeStack.size() > 0) {
          parentNode = treeStack.pop();
          moveNo = parentNode.getMoveNo() + 1;
        }
      }
      else {
      }
    }

    return game;
  }

  private String consumeUntil(String gameStr, int i) {
    StringBuffer rtrn = new StringBuffer();
    boolean insideComment = false;
    for (int j = i + 1; j < gameStr.length(); j++) {
      char chr = gameStr.charAt(j);
      if (insideComment) {
        if (']' == chr && gameStr.charAt(j - 1) != '\\') {
          insideComment = false;
        }
        rtrn.append(chr);
      }
      else {
        if ('C' == chr && '[' == gameStr.charAt(j + 1)) {
          insideComment = true;
          rtrn.append(chr);
        }
        else if (';' != chr && ')' != chr && '(' != chr) {
          rtrn.append(chr);
        }
        else {
          break;
        }
      }
    }
    return rtrn.toString().trim();
  }

  private GameNode parseToken(String token, final GameNode parentNode, Game game) {
    GameNode rtrnNode = new GameNode(parentNode);
    // replace delimiters
    token = prepareToken("'" + token + "'");

    // lets find all the properties
    Pattern p = Pattern.compile("([a-zA-Z]{1,})((\\[[^\\]]*\\]){1,})");
    Matcher m = p.matcher(token);
    while (m.find()) {
      String group = m.group();
      if (group.length() == 0)
        continue;

      String key = m.group(1);
      String value = m.group(2);
      if (value.startsWith("[")) {
        value = value.substring(1, value.length() - 1);
      }

      // these properties require some cleanup
      if ("AB".equals(key) || "AW".equals(key)) {
        // these come in as a list of coordinates while the first [ is cut off
        // and also the last ], easy to split by ][
        String[] list = value.split("\\]\\[");
        game.addProperty(key, String.join(",", list));
      }
      else if (generalProps.contains(key)) {
        game.addProperty(key, value);
      }
      else if (nodeProps.contains(key)) {
        rtrnNode.addProperty(key, cleanValue(value));
      }
      else if ("L".equals(key)) {
        log.debug("Not handling " + key + " = " + value);
      }
      else {
        throw new SgfParseException(
            "Ignoring property '" + m.group(1) + "'=" + m.group(2) + " Found it from '" + m.group(0) + "'");
      }
    }

    return rtrnNode;
  }

  private String cleanValue(String value) {
    String cleaned = value.replaceAll("\\\\;", ";");
    return cleaned;
  }

  private String prepareToken(String token) {
    token = token.replaceAll("\\\\\\[", "@@@@@");
    token = token.replaceAll("\\\\\\]", "#####");
    return token;
  }
}