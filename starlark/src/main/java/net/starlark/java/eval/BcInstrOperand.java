/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.starlark.java.eval;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;
import net.starlark.java.syntax.TokenKind;

/**
 * Describe instruction operands of the Starlark bytecode.
 *
 * <p>This code is used only when assertions are enabled, because proper instruction validation
 * might be expensive.
 */
class BcInstrOperand {
  /** Bytecode operand is an integer, stored in the bytecode. */
  static final Operands NUMBER = new NumberOperand();
  /**
   * Bytecode operand is logically a string, stored in the strings storage; the index is stored in
   * the bytecode.
   */
  static final Operands STRING = new StringOperand();
  /**
   * Bytecode operand is logically an object, stored in the strings storage; the index is stored in
   * the bytecode.
   */
  static final Operands OBJECT = new ObjectArg();

  /**
   * Bytecode operand is an input register. Note current implementation does not validate that it is
   * actually read, not write register, it is used mostly as a hint when bytecode is printed.
   *
   * <p>Operand of this type can be a non-negative integer for regular slot, or negative integer for
   * constants.
   */
  static final Operands IN_SLOT = new Register("r");
  /**
   * Bytecode operand is an input register. Note current implementation does not validate that it is
   * actually read, not write register, it is used mostly as a hint when bytecode is printed.
   *
   * <p>Operand of this type can be a non-negative integer for regular slot, or negative integer for
   * constants.
   */
  static final Operands IN_LOCAL = new Local("r");
  /**
   * Bytecode operand is an output register.
   *
   * <p>The value of this operand must be a non-negative integer.
   */
  static final Operands OUT_SLOT = new Register("w");

  /** Bytecode operand is a fixed integer, storing {@link TokenKind}. */
  static final Operands TOKEN_KIND = new KindArg();

  /** Either length-delimited slots or an object array. */
  static final Operands IN_LIST = new ListOperand();

  /** Operand is a fixed number storing the instruction pointer. */
  static final Operands ADDR = new AddrArg();

  private BcInstrOperand() {}

  /** Fixed of operands, e. g. a pair of operands used to describe a dict key and value. */
  static Operands fixed(Operands... operands) {
    return new FixedOperandsOpcode(operands);
  }

  /** Length-delimited operands, e. g. list constructor arguments. */
  static Operands lengthDelimited(Operands element) {
    return new LengthDelimited(element);
  }

  /**
   * Sequence of operands.
   *
   * <p>Note in Starlark bytecode, the opcode operands are variable length: The number of operands
   * depend not just on the opcode, but it is encoded in the previous operands. E. g. a list
   * constructor is encoded as a length delimited sequence of register operands.
   */
  abstract static class Operands {
    private Operands() {}

    /** This is low level operation, do not use directly. */
    abstract void print(OpcodePrinter visitor);

    /**
     * Given the offset of the operand, return the position after the operand. In another words,
     * determine operand code size.
     *
     * <p>For example, length-delimited operand may return the different number of ints depending on
     * the actual bytecode.
     */
    protected abstract void consume(BcInstrParser parser);

    /**
     * Get the number of integers occupied by this operands object at the given bytecode offset.
     *
     * <p>For example, length-delimited operand may return the different number of ints depending on
     * the actual bytecode.
     */
    int codeSize(int[] text, int ip) {
      BcInstrParser parser = new BcInstrParser(text, ip);
      consume(parser);
      return parser.getIp() - ip;
    }

    /** Get both instruction count for this operand and the string representation. */
    String toStringAndCount(
        BcInstrParser parser,
        List<String> strings,
        List<Object> constantRegs,
        List<Object> objects,
        OpcodePrinterFunctionContext fnCtx) {
      OpcodePrinter printer = new OpcodePrinter(parser, strings, constantRegs, objects, fnCtx);
      print(printer);
      return printer.sb.toString();
    }

    abstract static class Decoded {
      AddrArg.Decoded asAddr() {
        return (AddrArg.Decoded) this;
      }

      FixedOperandsOpcode.Decoded asFixed() {
        return (FixedOperandsOpcode.Decoded) this;
      }

      KindArg.Decoded asKind() {
        return (KindArg.Decoded) this;
      }

      LengthDelimited.Decoded asLengthDelimited() {
        return (LengthDelimited.Decoded) this;
      }

      NumberOperand.Decoded asNumber() {
        return (NumberOperand.Decoded) this;
      }

      ObjectArg.Decoded asObject() {
        return (ObjectArg.Decoded) this;
      }

      Register.Decoded asRegister() {
        return (Register.Decoded) this;
      }

      StringOperand.Decoded asString() {
        return (StringOperand.Decoded) this;
      }
    }

    abstract Decoded decode(BcInstrParser parser);
  }

  static class OpcodePrinterFunctionContext {
    private final ImmutableList<String> locals;
    private final ImmutableList<String> globals;
    private final ImmutableList<String> freeVars;

    public OpcodePrinterFunctionContext(
        ImmutableList<String> locals,
        ImmutableList<String> globals,
        ImmutableList<String> freeVars) {
      this.locals = locals;
      this.globals = globals;
      this.freeVars = freeVars;
    }
  }

  /** This class is package-private only because it is referenced from {@link Operands}. */
  private static class OpcodePrinter {

    private final BcInstrParser parser;
    private final List<String> strings;
    private final List<Object> constantRegs;
    private final List<Object> objects;
    private final OpcodePrinterFunctionContext fnCtx;
    private StringBuilder sb = new StringBuilder();

    private OpcodePrinter(
        BcInstrParser parser,
        List<String> strings,
        List<Object> constantRegs,
        List<Object> objects,
        OpcodePrinterFunctionContext fnCtx) {
      this.parser = parser;
      this.objects = objects;
      this.fnCtx = fnCtx;
      this.strings = strings;
      this.constantRegs = constantRegs;
    }

    private void append(String s) {
      sb.append(s);
    }
  }

  /** One word operand (e. g. register). */
  private abstract static class OneWordOperand extends Operands {
    @Override
    protected final void consume(BcInstrParser parser) {
      parser.nextInt();
    }
  }

  static class NumberOperand extends OneWordOperand {
    @Override
    public void print(OpcodePrinter visitor) {
      visitor.append(Integer.toString(visitor.parser.nextInt()));
    }

    static class Decoded extends Operands.Decoded {
      final int value;

      Decoded(int value) {
        this.value = value;
      }
    }

    @Override
    Decoded decode(BcInstrParser parser) {
      return new Decoded(parser.nextInt());
    }
  }

  static class StringOperand extends OneWordOperand {
    @Override
    public void print(OpcodePrinter visitor) {
      visitor.append(visitor.strings.get(visitor.parser.nextInt()));
    }

    static class Decoded extends Operands.Decoded {
      final int index;

      Decoded(int index) {
        this.index = index;
      }
    }

    @Override
    Decoded decode(BcInstrParser parser) {
      return new Decoded(parser.nextInt());
    }
  }

  static class Local extends OneWordOperand {
    /** r or w, for read or write */
    private final String label;

    private Local(String label) {
      this.label = label;
    }

    @Override
    void print(OpcodePrinter visitor) {
      new Register(label).print(visitor);
    }

    @Override
    Decoded decode(BcInstrParser parser) {
      return new Decoded(parser.nextInt());
    }

    static class Decoded extends Operands.Decoded {
      final int register;

      public Decoded(int register) {
        this.register = register;
      }
    }
  }

  static class Register extends OneWordOperand {
    /** r or w, for read or write */
    private final String label;

    private Register(String label) {
      this.label = label;
    }

    @Override
    public void print(OpcodePrinter visitor) {
      int reg = visitor.parser.nextInt();
      Object valueToPrint;
      int flag = reg & BcSlot.MASK;
      int index = reg & ~BcSlot.MASK;
      switch (flag) {
        case BcSlot.LOCAL_FLAG:
          if (index < visitor.fnCtx.locals.size()) {
            // local
            valueToPrint = "l$" + index + ":" + visitor.fnCtx.locals.get(index);
          } else {
            // temporary
            valueToPrint = "s$" + index;
          }
          break;
        case BcSlot.GLOBAL_FLAG:
          valueToPrint = "g$" + index + ":" + visitor.fnCtx.globals.get(index);
          break;
        case BcSlot.CELL_FLAG:
          valueToPrint = "c$" + index + ":" + visitor.fnCtx.locals.get(index);
          break;
        case BcSlot.FREE_FLAG:
          valueToPrint = "f$" + index + ":" + visitor.fnCtx.freeVars.get(index);
          break;
        case BcSlot.CONST_FLAG:
          Object constant = visitor.constantRegs.get(index);
          valueToPrint = "=" + Starlark.repr(constant);
          break;
        case BcSlot.NULL_FLAG:
          valueToPrint = "=null";
          break;
        default:
          throw new IllegalStateException("wrong slot");
      }
      visitor.sb.append(label).append(valueToPrint);
    }

    static class Decoded extends Operands.Decoded {
      final int register;

      public Decoded(int register) {
        this.register = register;
      }
    }

    @Override
    Decoded decode(BcInstrParser parser) {
      return new Decoded(parser.nextInt());
    }
  }

  static class KindArg extends OneWordOperand {
    @Override
    public void print(OpcodePrinter visitor) {
      visitor.sb.append(TokenKind.values()[visitor.parser.nextInt()]);
    }

    static class Decoded extends Operands.Decoded {
      final TokenKind tokenKind;

      public Decoded(TokenKind tokenKind) {
        this.tokenKind = tokenKind;
      }
    }

    @Override
    Decoded decode(BcInstrParser parser) {
      return new Decoded(parser.nextTokenKind());
    }
  }

  static class AddrArg extends OneWordOperand {
    private AddrArg() {}

    @Override
    public void print(OpcodePrinter visitor) {
      visitor.append("@" + visitor.parser.nextInt());
    }

    static class Decoded extends Operands.Decoded {
      final int addr;

      public Decoded(int addr) {
        this.addr = addr;
      }
    }

    @Override
    Decoded decode(BcInstrParser parser) {
      return new Decoded(parser.nextInt());
    }
  }

  static class ObjectArg extends OneWordOperand {
    @Override
    public void print(OpcodePrinter visitor) {
      int objectIndex = visitor.parser.nextInt();
      Object o =
          objectIndex < visitor.objects.size() ? visitor.objects.get(objectIndex) : "invalid";
      visitor.append("o" + objectIndex + "=" + o);
    }

    static class Decoded extends Operands.Decoded {
      final int index;

      public Decoded(int index) {
        this.index = index;
      }
    }

    @Override
    Decoded decode(BcInstrParser parser) {
      return new Decoded(parser.nextInt());
    }
  }

  static class FixedOperandsOpcode extends Operands {
    private final Operands[] operands;

    private FixedOperandsOpcode(Operands[] operands) {
      this.operands = operands;
    }

    @Override
    public void print(OpcodePrinter visitor) {
      visitor.append("(");
      for (int i = 0; i < operands.length; i++) {
        if (i != 0) {
          visitor.sb.append(" ");
        }
        Operands operand = operands[i];
        operand.print(visitor);
      }
      visitor.append(")");
    }

    @Override
    protected void consume(BcInstrParser parser) {
      for (Operands operand : operands) {
        operand.consume(parser);
      }
    }

    static class Decoded extends Operands.Decoded {
      final ImmutableList<Operands.Decoded> operands;

      Decoded(ImmutableList<Operands.Decoded> operands) {
        this.operands = operands;
      }
    }

    @Override
    Decoded decode(BcInstrParser parser) {
      return new Decoded(
          Arrays.stream(operands)
              .map(o -> o.decode(parser))
              .collect(ImmutableList.toImmutableList()));
    }
  }

  static class LengthDelimited extends Operands {
    private final Operands element;

    private LengthDelimited(Operands element) {
      this.element = element;
    }

    @Override
    public void print(OpcodePrinter visitor) {
      visitor.append("[");
      int size = visitor.parser.nextInt();
      for (int i = 0; i != size; ++i) {
        if (i != 0) {
          visitor.append(" ");
        }
        element.print(visitor);
      }
      visitor.append("]");
    }

    @Override
    protected void consume(BcInstrParser parser) {
      int size = parser.nextInt();
      for (int i = 0; i != size; ++i) {
        element.consume(parser);
      }
    }

    static class Decoded extends Operands.Decoded {
      private final ImmutableList<Operands.Decoded> elements;

      Decoded(ImmutableList<Operands.Decoded> elements) {
        this.elements = elements;
      }
    }

    @Override
    Decoded decode(BcInstrParser parser) {
      return new Decoded(
          IntStream.range(0, parser.nextInt())
              .mapToObj(i -> element.decode(parser))
              .collect(ImmutableList.toImmutableList()));
    }
  }

  /** Length-delimited slot or an array in object pool. */
  static class ListOperand extends Operands {

    @Override
    void print(OpcodePrinter visitor) {
      int size = visitor.parser.nextInt();
      if (size < 0) {
        visitor.append("o" + (1 - size));
      } else {
        visitor.append("[");
        for (int i = 0; i != size; ++i) {
          if (i != 0) {
            visitor.append(" ");
          }
          IN_SLOT.print(visitor);
        }
        visitor.append("]");
      }
    }

    @Override
    protected void consume(BcInstrParser parser) {
      int size = parser.nextInt();
      if (size >= 0) {
        for (int i = 0; i != size; ++i) {
          IN_SLOT.consume(parser);
        }
      }
    }

    @Override
    Operands.Decoded decode(BcInstrParser parser) {
      int size = parser.nextInt();
      if (size < 0) {
        return new Decoded(size, ArraysForStarlark.EMPTY_INT_ARRAY);
      } else {
        int[] slots = parser.nextInts(size);
        return new Decoded(size, slots);
      }
    }

    static class Decoded extends Operands.Decoded {
      final int size;
      final int[] slots;

      Decoded(int size, int[] slots) {
        if (size < 0) {
          Preconditions.checkArgument(slots.length == 0);
        } else {
          Preconditions.checkArgument(size == slots.length);
        }
        this.size = size;
        this.slots = slots;
      }
    }
  }
}
