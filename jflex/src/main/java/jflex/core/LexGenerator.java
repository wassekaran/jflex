/* * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * *
 * JFlex 1.7.1-SNAPSHOT                                                    *
 * Copyright (C) 1998-2018  Gerwin Klein <lsf@jflex.de>                    *
 * All rights reserved.                                                    *
 *                                                                         *
 * License: BSD                                                            *
 *                                                                         *
 * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * */

package jflex.core;

import static jflex.core.Options.encoding;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Paths;
import jflex.exceptions.GeneratorException;
import jflex.exceptions.MacroException;
import jflex.l10n.ErrorMessages;
import jflex.performance.Timer;

/**
 * This is the generator of JFlex, controlling the scanner generation process.
 *
 * @author Gerwin Klein
 * @author Régis Décamps
 * @version JFlex 1.7.1-SNAPSHOT
 */
public class LexGenerator {

  /**
   * Generates a scanner for the specified input file.
   *
   * @param inputFile a file containing a lexical specification to generate a scanner for.
   * @return the file name of the generated Java sources.
   */
  public static String generate(File inputFile) {

    Out.resetCounters();

    Timer totalTime = new Timer();
    Timer time = new Timer();

    totalTime.start();

    try (Reader inputReader =
        new InputStreamReader(Files.newInputStream(Paths.get(inputFile.toString())), encoding)) {
      Out.println(jflex.l10n.ErrorMessages.READING, inputFile.toString());
      LexScan scanner = new LexScan(inputReader);
      scanner.setFile(inputFile);
      LexParse parser = new LexParse(scanner);

      NFA nfa = (NFA) parser.parse().value;

      Out.checkErrors();

      if (Options.dump)
        Out.dump(
            jflex.l10n.ErrorMessages.get(jflex.l10n.ErrorMessages.NFA_IS) + Out.NL + nfa + Out.NL);

      if (Options.dot) nfa.writeDot(Emitter.normalize("nfa.dot", null)); // $NON-NLS-1$

      Out.println(jflex.l10n.ErrorMessages.NFA_STATES, nfa.numStates);

      time.start();
      DFA dfa = nfa.getDFA();
      time.stop();
      Out.time(jflex.l10n.ErrorMessages.DFA_TOOK, time);

      dfa.checkActions(scanner, parser);

      if (Options.dump)
        Out.dump(
            jflex.l10n.ErrorMessages.get(jflex.l10n.ErrorMessages.DFA_IS) + Out.NL + dfa + Out.NL);

      if (Options.dot) dfa.writeDot(Emitter.normalize("dfa-big.dot", null)); // $NON-NLS-1$

      Out.checkErrors();

      time.start();
      dfa.minimize();
      time.stop();

      Out.time(jflex.l10n.ErrorMessages.MIN_TOOK, time);

      if (Options.dump)
        Out.dump(jflex.l10n.ErrorMessages.get(jflex.l10n.ErrorMessages.MIN_DFA_IS) + Out.NL + dfa);

      if (Options.dot) dfa.writeDot(Emitter.normalize("dfa-min.dot", null)); // $NON-NLS-1$

      time.start();

      Emitter emitter = new Emitter(inputFile, parser, dfa);
      emitter.emit();

      time.stop();

      Out.time(jflex.l10n.ErrorMessages.WRITE_TOOK, time);

      totalTime.stop();

      Out.time(jflex.l10n.ErrorMessages.TOTAL_TIME, totalTime);
      return emitter.outputFileName;
    } catch (ScannerException e) {
      Out.error(e.file, e.message, e.line, e.column);
      throw new GeneratorException();
    } catch (MacroException e) {
      Out.error(e.getMessage());
      throw new GeneratorException();
    } catch (IOException e) {
      Out.error(jflex.l10n.ErrorMessages.IO_ERROR, e.toString());
      throw new GeneratorException();
    } catch (OutOfMemoryError e) {
      Out.error(ErrorMessages.OUT_OF_MEMORY);
      throw new GeneratorException();
    } catch (GeneratorException e) {
      throw new GeneratorException();
    } catch (Exception e) {
      e.printStackTrace();
      throw new GeneratorException();
    }
  }
}
