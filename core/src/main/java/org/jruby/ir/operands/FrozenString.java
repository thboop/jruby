package org.jruby.ir.operands;

import org.jruby.RubyString;
import org.jruby.RubySymbol;
import org.jruby.ir.IRVisitor;
import org.jruby.ir.persistence.IRReaderDecoder;
import org.jruby.ir.persistence.IRWriterEncoder;
import org.jruby.ir.runtime.IRRuntimeHelpers;
import org.jruby.ir.transformations.inlining.CloneInfo;
import org.jruby.runtime.Helpers;
import org.jruby.runtime.ThreadContext;
import org.jruby.util.ByteList;
import org.jruby.util.StringSupport;

import java.nio.charset.UnsupportedCharsetException;
import java.util.List;

/**
 * Represents a frozen string value.
 */
public class FrozenString extends ImmutableLiteral<RubyString> implements Stringable, StringLiteral {
    public final ByteList bytelist;
    public final int      coderange;
    public final String file;
    public final int line;

    public String string;

    /**
     * Used by persistence and by .freeze optimization
     */
    public FrozenString(ByteList bytelist, int coderange, String file, int line) {
        super();

        this.bytelist = bytelist;
        this.coderange = coderange;
        this.file = file;
        this.line = line;
    }

    public FrozenString(RubySymbol symbol) {
        this(symbol.getBytes());
    }

    /**
     * IRBuild.buildGetDefinition returns a frozen string and this is for all intern'd Java strings.
     */
    public FrozenString(String s) {
        this(ByteList.create(s));
    }

    protected FrozenString(ByteList byteList) {
        super();

        this.bytelist = byteList;
        this.coderange = StringSupport.CR_7BIT;
        this.file = "<dummy>";
        this.line = -1;
    }

    // If Encoding has an instance of a Charset can it ever raise unsupportedcharsetexception? because this
    // helper called copes with charset == null...
    private static String internedStringFromByteList(ByteList val) {
        try {
            return Helpers.byteListToString(val).intern();
        } catch (UnsupportedCharsetException e) {
            return val.toString().intern();
        }
    }

    @Override
    public OperandType getOperandType() {
        return OperandType.FROZEN_STRING;
    }

    @Override
    public boolean hasKnownValue() {
        return true;
    }

    @Override
    public int hashCode() {
        return bytelist.hashCode();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof FrozenString && bytelist.equals(((FrozenString) other).bytelist) && coderange == ((FrozenString) other).coderange;
    }

    @Override
    public String toString() {
        return "frozen:\"" + bytelist + "\"";
    }

    @Override
    public Operand cloneForInlining(CloneInfo ii) {
        return this;
    }

    @Override
    public RubyString createCacheObject(ThreadContext context) {
        return IRRuntimeHelpers.newFrozenString(context, bytelist, coderange, file, line);
    }

    // allways call createCacheObject (GH-7229)
    @Override
    public boolean isCached() {
        return false;
    }

    @Override
    public void visit(IRVisitor visitor) {
        visitor.FrozenString(this);
    }

    public ByteList getByteList() {
        return bytelist;
    }

    public String getString() {
        String cached = string;
        if (cached == null) {
            string = cached = internedStringFromByteList(bytelist);
        }
        return cached;
    }

    public String getFile() {
        return file;
    }

    public int getLine() {
        return line;
    }

    @Override
    public void encode(IRWriterEncoder e) {
        super.encode(e);
        e.encode(bytelist);
        e.encode(coderange);
        e.encode(file);
        e.encode(line);
    }

    public static FrozenString decode(IRReaderDecoder d) {
        return new FrozenString(d.decodeByteList(), d.decodeInt(), d.decodeString(), d.decodeInt());
    }

    public int getCodeRange() {
        return coderange;
    }

    @Override
    public boolean isTruthyImmediate() {
        return true;
    }
}
