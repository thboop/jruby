package org.jruby.ir.instructions;

import org.jruby.ir.IRVisitor;
import org.jruby.ir.Operation;
import org.jruby.ir.operands.Operand;
import org.jruby.ir.operands.Variable;
import org.jruby.ir.persistence.IRReaderDecoder;
import org.jruby.ir.persistence.IRWriterEncoder;
import org.jruby.ir.transformations.inlining.CloneInfo;
import org.jruby.parser.StaticScope;
import org.jruby.runtime.DynamicScope;
import org.jruby.runtime.Helpers;
import org.jruby.runtime.ThreadContext;
import org.jruby.runtime.builtin.IRubyObject;

// This represents an array that is used solely during arguments construction
//   * Array + Splat ([1,2,3], *[5,6,7])
// This used to be an operand, but since to_a can be called as part of
// building the args-cat/push value, this is not really side-effect free.
public class BuildCompoundArrayInstr extends TwoOperandResultBaseInstr {
    private final boolean isArgsPush;
    // This array involves a last element known to be a keyword.
    private final boolean usesKeywordRest;

    public BuildCompoundArrayInstr(Variable result, Operand a1, Operand a2, boolean isArgsPush, boolean usesKeywordRest) {
        super(Operation.BUILD_COMPOUND_ARRAY, result, a1, a2);
        this.isArgsPush = isArgsPush;
        this.usesKeywordRest = usesKeywordRest;
    }

    public Operand getAppendingArg() {
        return getOperand1();
    }

    public Operand getAppendedArg() {
        return getOperand2();
    }

    public boolean isArgsPush() { return isArgsPush; }

    public boolean usesKeywordRest() {
        return usesKeywordRest;
    }

    @Override
    public Instr clone(CloneInfo ii) {
        return new BuildCompoundArrayInstr(ii.getRenamedVariable(result), getAppendingArg().cloneForInlining(ii),
                getAppendedArg().cloneForInlining(ii), isArgsPush, usesKeywordRest);
    }

    @Override
    public void encode(IRWriterEncoder e) {
        super.encode(e);
        e.encode(getAppendingArg());
        e.encode(getAppendedArg());
        e.encode(isArgsPush());
        e.encode(usesKeywordRest());
    }

    public static BuildCompoundArrayInstr decode(IRReaderDecoder d) {
        return new BuildCompoundArrayInstr(d.decodeVariable(), d.decodeOperand(), d.decodeOperand(), d.decodeBoolean(), d.decodeBoolean());
    }

    @Override
    public Object interpret(ThreadContext context, StaticScope currScope, DynamicScope currDynScope, IRubyObject self, Object[] temp) {
        IRubyObject v1 = (IRubyObject)getAppendingArg().retrieve(context, self, currScope, currDynScope, temp);
        IRubyObject v2 = (IRubyObject) getAppendedArg().retrieve(context, self, currScope, currDynScope, temp);
        return isArgsPush ? Helpers.argsPush(context, v1, v2, usesKeywordRest) : Helpers.argsCat(context, v1, v2);
    }

    @Override
    public void visit(IRVisitor visitor) {
        visitor.BuildCompoundArrayInstr(this);
    }

    @Override
    public String[] toStringNonOperandArgs() {
        return new String[] { "type: " + (isArgsPush ? "push" : "cat")};
    }
}
