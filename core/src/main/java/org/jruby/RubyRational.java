/***** BEGIN LICENSE BLOCK *****
 * Version: EPL 2.0/GPL 2.0/LGPL 2.1
 *
 * The contents of this file are subject to the Eclipse Public
 * License Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of
 * the License at http://www.eclipse.org/legal/epl-v20.html
 *
 * Software distributed under the License is distributed on an "AS
 * IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * rights and limitations under the License.
 *
 * Alternatively, the contents of this file may be used under the terms of
 * either of the GNU General Public License Version 2 or later (the "GPL"),
 * or the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
 * in which case the provisions of the GPL or the LGPL are applicable instead
 * of those above. If you wish to allow use of your version of this file only
 * under the terms of either the GPL or the LGPL, and not to allow others to
 * use your version of this file under the terms of the EPL, indicate your
 * decision by deleting the provisions above and replace them with the notice
 * and other provisions required by the GPL or the LGPL. If you do not delete
 * the provisions above, a recipient may use your version of this file under
 * the terms of any one of the EPL, the GPL or the LGPL.
 ***** END LICENSE BLOCK *****/

package org.jruby;

import java.io.IOException;
import java.math.BigInteger;
import java.math.RoundingMode;

import org.jcodings.specific.ASCIIEncoding;
import org.jcodings.specific.USASCIIEncoding;
import org.jruby.anno.JRubyClass;
import org.jruby.anno.JRubyMethod;
import org.jruby.api.Convert;
import org.jruby.api.JRubyAPI;
import org.jruby.ast.util.ArgsUtil;
import org.jruby.exceptions.RaiseException;
import org.jruby.runtime.Arity;
import org.jruby.runtime.CallSite;
import org.jruby.runtime.ClassIndex;
import org.jruby.runtime.JavaSites;
import org.jruby.runtime.ObjectMarshal;
import org.jruby.runtime.ThreadContext;
import org.jruby.runtime.Visibility;
import org.jruby.runtime.builtin.IRubyObject;
import org.jruby.runtime.marshal.MarshalDumper;
import org.jruby.runtime.marshal.MarshalLoader;
import org.jruby.util.ByteList;
import org.jruby.util.Numeric;
import org.jruby.util.TypeConverter;
import org.jruby.util.io.RubyInputStream;
import org.jruby.util.io.RubyOutputStream;

import static org.jruby.api.Convert.asBoolean;
import static org.jruby.api.Convert.asFixnum;
import static org.jruby.api.Convert.asFloat;
import static org.jruby.api.Convert.checkToInteger;
import static org.jruby.api.Convert.toDouble;
import static org.jruby.api.Create.newArray;
import static org.jruby.api.Create.newString;
import static org.jruby.api.Define.defineClass;
import static org.jruby.api.Error.argumentError;
import static org.jruby.api.Error.runtimeError;
import static org.jruby.api.Error.typeError;
import static org.jruby.api.Warn.warn;
import static org.jruby.ast.util.ArgsUtil.hasExceptionOption;
import static org.jruby.runtime.Helpers.invokedynamic;
import static org.jruby.runtime.invokedynamic.MethodNames.HASH;
import static org.jruby.util.Numeric.f_abs;
import static org.jruby.util.Numeric.f_add;
import static org.jruby.util.Numeric.f_cmp;
import static org.jruby.util.Numeric.f_div;
import static org.jruby.util.Numeric.f_equal;
import static org.jruby.util.Numeric.f_expt;
import static org.jruby.util.Numeric.f_floor;
import static org.jruby.util.Numeric.f_gcd;
import static org.jruby.util.Numeric.f_idiv;
import static org.jruby.util.Numeric.f_integer_p;
import static org.jruby.util.Numeric.f_minus_one_p;
import static org.jruby.util.Numeric.f_mul;
import static org.jruby.util.Numeric.f_negate;
import static org.jruby.util.Numeric.f_negative_p;
import static org.jruby.util.Numeric.f_odd_p;
import static org.jruby.util.Numeric.f_one_p;
import static org.jruby.util.Numeric.f_sub;
import static org.jruby.util.Numeric.f_to_i;
import static org.jruby.util.Numeric.f_to_r;
import static org.jruby.util.Numeric.f_truncate;
import static org.jruby.util.Numeric.f_xor;
import static org.jruby.util.Numeric.f_zero_p;
import static org.jruby.util.Numeric.i_gcd;
import static org.jruby.util.Numeric.i_ilog2;
import static org.jruby.util.Numeric.k_exact_p;
import static org.jruby.util.Numeric.k_integer_p;
import static org.jruby.util.Numeric.k_numeric_p;
import static org.jruby.util.Numeric.ldexp;
import static org.jruby.util.Numeric.nurat_rationalize_internal;
import static org.jruby.util.RubyStringBuilder.str;

/**
 * Ruby Rational impl (MRI: rational.c).
 */
@JRubyClass(name = "Rational", parent = "Numeric")
public class RubyRational extends RubyNumeric {
    
    public static RubyClass createRationalClass(ThreadContext context, RubyClass Numeric) {
        return defineClass(context, "Rational", Numeric, RubyRational::new).
                reifiedClass(RubyRational.class).
                marshalWith(RATIONAL_MARSHAL).
                kindOf(new RubyModule.JavaClassKindOf(RubyRational.class)).
                classIndex(ClassIndex.RATIONAL).
                defineMethods(context, RubyRational.class).
                tap(c -> c.singletonClass(context).undefMethods(context, "allocate", "new"));
    }

    private RubyRational(Ruby runtime, RubyClass clazz, RubyInteger num, RubyInteger den) {
        super(runtime, clazz);
        this.num = num;
        this.den = den;
    }

    private RubyRational(Ruby runtime, RubyClass clazz) {
        super(runtime, clazz);
        RubyFixnum zero = RubyFixnum.zero(runtime);
        this.num = zero;
        this.den = zero;
    }

    /** rb_rational_raw
     * 
     */
    public static RubyRational newRationalRaw(Ruby runtime, IRubyObject x, IRubyObject y) {
        return newRational(runtime, runtime.getRational(), x, y);
    }

    /** rb_rational_raw1
     * 
     */
    static RubyRational newRationalRaw(Ruby runtime, IRubyObject x) {
        return newRational(runtime, runtime.getRational(), x, RubyFixnum.one(runtime));
    }

    /** rb_rational_new1
     * 
     */
    static RubyNumeric newRationalCanonicalize(ThreadContext context, RubyInteger x) {
        return (RubyNumeric) newRationalCanonicalize(context, x, RubyFixnum.one(context.runtime));
    }

    /** rb_rational_new
     * 
     */
    public static IRubyObject newRationalCanonicalize(ThreadContext context, RubyInteger x, RubyInteger y) {
        return canonicalizeInternal(context, context.runtime.getRational(), x, y);
    }

    public static IRubyObject newRationalCanonicalize(ThreadContext context, IRubyObject x, IRubyObject y) {
        return canonicalizeInternal(context, context.runtime.getRational(), (RubyInteger) x, (RubyInteger) y);
    }

    public static IRubyObject newRationalCanonicalize(ThreadContext context, long x, long y) {
        return canonicalizeInternal(context, context.runtime.getRational(), x, y);
    }
    public static IRubyObject newRationalCanonicalize(ThreadContext context, long x) {
        return canonicalizeInternal(context, context.runtime.getRational(), x, 1);
    }

    static RubyNumeric newRationalNoReduce(ThreadContext context, RubyInteger x, RubyInteger y) {
        return canonicalizeInternalNoReduce(context, context.runtime.getRational(), x, y);
    }

    /** f_rational_new_no_reduce2
     * 
     */
    private static RubyNumeric newRationalNoReduce(ThreadContext context, RubyClass clazz, RubyInteger x, RubyInteger y) {
        return canonicalizeInternalNoReduce(context, clazz, x, y);
    }

    /** f_rational_new_bang2
     * 
     */
    private static RubyRational newRationalBang(ThreadContext context, RubyClass clazz, IRubyObject x, IRubyObject y) {
        assert !f_negative_p(context, y) && !(f_zero_p(context, y));
        return newRational(context.runtime, clazz, x, y);
    }

    /** f_rational_new_bang1
     * 
     */
    private static RubyRational newRationalBang(ThreadContext context, RubyClass clazz, IRubyObject x) {
        return newRationalBang(context, clazz, x, asFixnum(context, 1));
    }

    private static RubyRational newRationalBang(ThreadContext context, RubyClass clazz, long x) {
        return newRationalBang(context, clazz, asFixnum(context, x), asFixnum(context, 1));
    }

    @Override
    public ClassIndex getNativeClassIndex() {
        return ClassIndex.RATIONAL;
    }
    
    private RubyInteger num;
    private RubyInteger den;

    /** nurat_canonicalization
     *
     */
    private static boolean canonicalization = false;
    public static void setCanonicalization(boolean canonical) {
        canonicalization = canonical;
    }

    /** nurat_int_check
     * 
     */
    private static RubyInteger intCheck(ThreadContext context, IRubyObject num) {
        if (num instanceof RubyInteger) return (RubyInteger) num;
        if (!(num instanceof RubyNumeric) || !integer_p_site(context).call(context, num, num).isTrue()) { // num.integer?
            throw typeError(context, "not an integer");
        }
        return num.convertToInteger();
    }

    private static CallSite integer_p_site(ThreadContext context) {
        return context.sites.Numeric.integer;
    }

    /** nurat_int_value
     * 
     */
    static IRubyObject intValue(ThreadContext context, IRubyObject num, boolean raise) {
        RubyInteger i = RubyInteger.toInteger(context, num);

        if (i == null) {
            if (raise) throw typeError(context, "can't convert ", num, " into Rational");
            return context.nil;
        }

        return i;
    }
    
    /** nurat_s_canonicalize_internal
     * 
     */
    private static RubyNumeric canonicalizeInternal(ThreadContext context, RubyClass clazz, RubyInteger num, RubyInteger den) {
        if (canonicalizeShouldNegate(context, den)) {
            num = num.negate(context);
            den = den.negate(context);
        }

        RubyInteger gcd = f_gcd(context, num, den);
        RubyInteger _num = (RubyInteger) num.idiv(context, gcd);
        RubyInteger _den = (RubyInteger) den.idiv(context, gcd);

        if (Numeric.CANON && canonicalization && f_one_p(context, _den)) return _num;

        return newRational(context.runtime, clazz, _num, _den);
    }

    private static RubyNumeric canonicalizeInternal(ThreadContext context, RubyClass clazz, long num, long den) {
        if (den == 0) throw context.runtime.newZeroDivisionError();

        if (num == Long.MIN_VALUE && den == Long.MIN_VALUE)
            canonicalizeInternal(context, clazz, asFixnum(context, num), asFixnum(context, den));
        long gcd = i_gcd(num, den);
        RubyInteger _num = (RubyInteger) asFixnum(context, num).idiv(context, gcd);
        RubyInteger _den = (RubyInteger) asFixnum(context, den).idiv(context, gcd);

        if (Numeric.CANON && canonicalization && _den.asLong(context) == 1) return _num;

        return newRational(context.runtime, clazz, _num, _den);
    }

    /** nurat_s_canonicalize_internal_no_reduce
     * 
     */
    private static RubyNumeric canonicalizeInternalNoReduce(ThreadContext context, RubyClass clazz,
                                                            RubyInteger num, RubyInteger den) {
        // MRI: nurat_canonicalize, negation part
        if (canonicalizeShouldNegate(context, den)) {
            num = num.negate(context);
            den = den.negate(context);
        }

        if (Numeric.CANON && canonicalization && f_one_p(context, den)) return num;

        return newRational(context.runtime, clazz, num, den);
    }

    // MRI: nurat_canonicalize, value check part
    private static boolean canonicalizeShouldNegate(ThreadContext context, RubyInteger den) {
        final int signum = den.signum(context);
        if (signum == 0) throw context.runtime.newZeroDivisionError();
        return signum < 0;
    }
    
    /** nurat_s_new
     * 
     */
    @Deprecated
    public static IRubyObject newInstance(ThreadContext context, IRubyObject clazz, IRubyObject[]args) {
        switch (args.length) {
            case 1: return newInstance(context, (RubyClass) clazz, args[0]);
            case 2: return newInstance(context, (RubyClass) clazz, args[0], args[1]);
        }
        Arity.raiseArgumentError(context, args.length, 1, 1);
        return null;
    }

    @Deprecated // confusing parameters
    public static IRubyObject newInstance(ThreadContext context, IRubyObject clazz, IRubyObject num) {
        return newInstance(context, (RubyClass) clazz, num);
    }

    static IRubyObject newInstance(ThreadContext context, RubyClass clazz, IRubyObject num) {
        return newInstance(context, clazz, num, true);
    }

    static IRubyObject newInstance(ThreadContext context, RubyClass clazz, IRubyObject num, boolean raise) {
        IRubyObject maybeInt = intValue(context, num, raise);

        if (maybeInt.isNil()) return maybeInt;
        return canonicalizeInternal(context, clazz, maybeInt.convertToInteger(), RubyFixnum.one(context.runtime));
    }

    @Deprecated
    public static IRubyObject newInstance(ThreadContext context, IRubyObject clazz, IRubyObject num, IRubyObject den) {
        return newInstance(context, (RubyClass) clazz, num, den);
    }

    static IRubyObject newInstance(ThreadContext context, RubyClass clazz, IRubyObject num, IRubyObject den) {
        return newInstance(context, clazz, num, den, true);
    }

    static IRubyObject newInstance(ThreadContext context, RubyClass clazz, IRubyObject num, IRubyObject den, boolean raise) {
        IRubyObject maybeInt1 = intValue(context, num, raise);
        IRubyObject maybeInt2 = intValue(context, den, raise);

        if (maybeInt1.isNil()) return maybeInt1;
        if (maybeInt2.isNil()) return maybeInt2;

        return canonicalizeInternal(context, clazz, maybeInt1.convertToInteger(), maybeInt2.convertToInteger());
    }

    static RubyNumeric newInstance(ThreadContext context, RubyClass clazz, RubyInteger num, RubyInteger den) {
        return canonicalizeInternal(context, clazz, num, den);
    }

    public static RubyNumeric newInstance(ThreadContext context, RubyInteger num, RubyInteger den) {
        return canonicalizeInternal(context, context.runtime.getRational(), num, den);
    }

    public static RubyNumeric newInstance(ThreadContext context, RubyInteger num) {
        return canonicalizeInternal(context, context.runtime.getRational(), num, RubyFixnum.one(context.runtime));
    }

    /** rb_Rational1
     * 
     */
    public static IRubyObject newRationalConvert(ThreadContext context, IRubyObject x) {
        return newRationalConvert(context, x, RubyFixnum.one(context.runtime));
    }

    /** rb_Rational/rb_Rational2
     * 
     */
    public static IRubyObject newRationalConvert(ThreadContext context, IRubyObject x, IRubyObject y) {
        return convert(context, context.runtime.getRational(), x, y);
    }
    
    public static RubyRational newRational(Ruby runtime, long x, long y) {
        RubyRational rat = new RubyRational(runtime, runtime.getRational(), runtime.newFixnum(x), runtime.newFixnum(y));
        rat.setFrozen(true);
        return rat;
    }

    static RubyRational newRational(Ruby runtime, RubyClass clazz, IRubyObject x, IRubyObject y) {
        RubyRational rat = new RubyRational(runtime, clazz, x.convertToInteger(), y.convertToInteger());
        rat.setFrozen(true);
        return rat;
    }

    public static IRubyObject rationalCanonicalize(ThreadContext context, IRubyObject x) {
        if (x instanceof RubyRational) {
            RubyRational rational = (RubyRational) x;
            if (f_one_p(context, rational.den)) return rational.num;
        }
        return x;
    }
    
    @Deprecated
    public static IRubyObject convert(ThreadContext context, IRubyObject clazz, IRubyObject[]args) {
        switch (args.length) {
        case 1: return convert(context, clazz, args[0]);        
        case 2: return convert(context, clazz, args[0], args[1]);
        }
        Arity.raiseArgumentError(context, args.length, 1, 1);
        return null;
    }

    /** nurat_s_convert
     * 
     */
    @JRubyMethod(name = "convert", meta = true, visibility = Visibility.PRIVATE)
    public static IRubyObject convert(ThreadContext context, IRubyObject recv, IRubyObject a1) {
        if (a1 == context.nil) throw typeError(context, "can't convert nil into Rational");

        return convertCommon(context, (RubyClass) recv, a1, context.nil, true);
    }

    /** nurat_s_convert
     *
     */
    @JRubyMethod(name = "convert", meta = true, visibility = Visibility.PRIVATE)
    public static IRubyObject convert(ThreadContext context, IRubyObject recv, IRubyObject a1, IRubyObject a2) {
        IRubyObject maybeKwargs = ArgsUtil.getOptionsArg(context.runtime, a2, false);
        boolean raise = true;
        IRubyObject nil = context.nil;

        if (maybeKwargs.isNil()) {
            if (a1 == nil || a2 == nil) throw typeError(context, "can't convert nil into Rational");
        } else {
            a2 = nil;
            raise = hasExceptionOption(context, maybeKwargs, raise);

            if (a1 == nil) {
                if (raise) throw typeError(context, "can't convert nil into Rational");
                return nil;
            }
        }

        return convertCommon(context, (RubyClass) recv, a1, a2, raise);
    }

    /** nurat_s_convert
     * 
     */
    @JRubyMethod(name = "convert", meta = true, visibility = Visibility.PRIVATE)
    public static IRubyObject convert(ThreadContext context, IRubyObject recv, IRubyObject a1, IRubyObject a2, IRubyObject kwargs) {
        IRubyObject maybeKwargs = ArgsUtil.getOptionsArg(context.runtime, kwargs, false);
        if (maybeKwargs.isNil()) throw argumentError(context, 3, 1, 2);

        IRubyObject exception = ArgsUtil.extractKeywordArg(context, "exception", (RubyHash) maybeKwargs);
        boolean raise = exception.isNil() ? true : exception.isTrue();
        IRubyObject nil = context.nil;

        if (a1 == nil || a2 == nil) {
            if (raise) throw typeError(context, "can't convert nil into Rational");
            return nil;
        }

        return convertCommon(context, (RubyClass) recv, a1, a2, raise);
    }
    
    private static IRubyObject convertCommon(ThreadContext context, RubyClass clazz, IRubyObject a1, IRubyObject a2, boolean raise) {
        if (a1 instanceof RubyComplex) {
            RubyComplex a1c = (RubyComplex) a1;
            if (k_exact_p(a1c.getImage()) && f_zero_p(context, a1c.getImage())) a1 = a1c.getReal();
        }
        if (a2 instanceof RubyComplex) {
            RubyComplex a2c = (RubyComplex) a2;
            if (k_exact_p(a2c.getImage()) && f_zero_p(context, a2c.getImage())) a2 = a2c.getReal();
        }

        if (a1 instanceof RubyInteger) {
            // do nothing
        } else if (a1 instanceof RubyFloat) {
            a1 = ((RubyFloat) a1).to_r(context); // f_to_r
        } else if (a1 instanceof RubyString) {
            a1 = str_to_r_strict(context, (RubyString) a1, raise);
            if (!raise && a1.isNil()) return a1;
        } else if (a1 instanceof RubyObject && !a1.respondsTo("to_r")) {
            try {
                IRubyObject tmp = checkToInteger(context, a1);
                if (!tmp.isNil()) {
                    a1 = tmp;
                }
            } catch (RaiseException re) {
                context.setErrorInfo(context.nil);
            }
        }

        if (a2 instanceof RubyInteger) {
            // do nothing
        } else if (a2 instanceof RubyFloat) {
            a2 = ((RubyFloat) a2).to_r(context); // f_to_r
        } else if (a2 instanceof RubyString) {
            a2 = str_to_r_strict(context, (RubyString) a2, raise);
            if (!raise && a2.isNil()) return a2;
        } else if (!a2.isNil() & a2 instanceof RubyObject && !a2.respondsTo("to_r")) {
            try {
                IRubyObject tmp = checkToInteger(context, a2);
                if (!tmp.isNil()) {
                    a2 = tmp;
                }
            } catch (RaiseException re) {
                context.setErrorInfo(context.nil);
            }
        }

        if (a1 instanceof RubyRational) {
            if (a2 == context.nil || (k_exact_p(a2) && f_one_p(context, a2))) return a1;
        }

        RubyClass rationalClazz = context.runtime.getRational();
        if (a2 == context.nil) {
            if (!(a1 instanceof RubyNumeric && f_integer_p(context, (RubyNumeric) a1))) {
                if (!raise) {
                    try {
                        IRubyObject ret = TypeConverter.convertToType(context, a1, rationalClazz, sites(context).to_r_checked);
                        return ret;
                    } catch (RaiseException re) {
                        context.setErrorInfo(context.nil);
                        return context.nil;
                    }
                }
                return TypeConverter.convertToType(context, a1, rationalClazz, sites(context).to_r_checked);
            }
        } else {
            if (!(a1 instanceof RubyNumeric)) {
                try {
                    a1 = TypeConverter.convertToType(context, a1, rationalClazz, sites(context).to_r_checked);
                } catch (RaiseException re) {
                    if (!raise) {
                        context.setErrorInfo(context.nil);
                        return context.nil;
                    } else {
                        throw re;
                    }
                }
            }

            if (!(a2 instanceof RubyNumeric)) {
                try {
                    a2 = TypeConverter.convertToType(context, a2, rationalClazz, sites(context).to_r_checked);
                } catch (RaiseException re) {
                    if (!raise) {
                        context.setErrorInfo(context.nil);
                        return context.nil;
                    } else {
                        throw re;
                    }
                }
            }

            if ((a1 instanceof RubyNumeric && a2 instanceof RubyNumeric) &&
                (!f_integer_p(context, (RubyNumeric) a1) || !f_integer_p(context, (RubyNumeric) a2))) {

                try {
                    IRubyObject tmp = TypeConverter.convertToType(context, a1, rationalClazz, sites(context).to_r_checked, true);
                    a1 = tmp instanceof RubyRational ? tmp : context.nil;
                } catch(RaiseException e) {
                    context.setErrorInfo(context.nil);
                }

                return f_div(context, a1, a2);
            }
        }

        a1 = intCheck(context, a1);

        if (a2.isNil()) {
            a2 = RubyFixnum.one(context.runtime);
        } else if (!(a2 instanceof RubyInteger) && !raise) {
            return context.nil;
        } else {
            a2 = intCheck(context, a2);
        }

        return newInstance(context, clazz, a1, a2, raise);
    }

    /** nurat_numerator
     * 
     */
    @JRubyMethod(name = "numerator")
    @Override
    public IRubyObject numerator(ThreadContext context) {
        return num;
    }

    /** nurat_denominator
     * 
     */
    @JRubyMethod(name = "denominator")
    @Override
    public IRubyObject denominator(ThreadContext context) {
        return den;
    }

    public RubyInteger getNumerator() {
        return num;
    }

    public RubyInteger getDenominator() {
        return den;
    }

    public RubyRational convertToRational(ThreadContext context) { return this; }

    @Override
    public IRubyObject zero_p(ThreadContext context) {
        return asBoolean(context, isZero(context));
    }

    @Override
    public final boolean isZero(ThreadContext context) {
        return num.isZero(context);
    }

    @Override
    public IRubyObject nonzero_p(ThreadContext context) {
        return isZero(context) ? context.nil : this;
    }

    @Override
    public IRubyObject isNegative(ThreadContext context) {
        return asBoolean(context, isNegativeNumber(context));
    }

    @Override
    public IRubyObject isPositive(ThreadContext context) {
        return asBoolean(context, isPositiveNumber(context));
    }

    @Override
    public boolean isNegativeNumber(ThreadContext context) {
        return signum(context) < 0;
    }

    @Override
    public boolean isPositiveNumber(ThreadContext context) {
        return signum(context) > 0;
    }

    @Deprecated(since = "10.0")
    public final int signum() {
        return signum(getCurrentContext());
    }

    public final int signum(ThreadContext context) {
        return num.signum(context);
    }

    /** f_imul
     * 
     */
    private static RubyInteger f_imul(ThreadContext context, long a, long b) {
        if (a == 0 || b == 0) return asFixnum(context, 0);
        if (a == 1) return asFixnum(context, b);
        if (b == 1) return asFixnum(context, a);

        long c = a * b;
        return c / a != b ?
                (RubyInteger) RubyBignum.newBignum(context.runtime, a).op_mul(context, b) :
                asFixnum(context, c);
    }
    
    /** f_addsub
     * 
     */
    private static RubyNumeric f_addsub(ThreadContext context, RubyClass metaClass,
                                        RubyInteger anum, RubyInteger aden, RubyInteger bnum, RubyInteger bden,
                                        final boolean plus) {
        RubyInteger newNum, newDen, g, a, b;
        if (anum instanceof RubyFixnum anumf && aden instanceof RubyFixnum adenf &&
            bnum instanceof RubyFixnum bnumf && bden instanceof RubyFixnum bdenf) {
            long an = anumf.getValue();
            long ad = adenf.getValue();
            long bn = bnumf.getValue();
            long bd = bdenf.getValue();
            long ig = i_gcd(ad, bd);

            g = asFixnum(context, ig);
            a = f_imul(context, an, bd / ig);
            b = f_imul(context, bn, ad / ig);
        } else {
            g = f_gcd(context, aden, bden);
            a = f_mul(context, anum, f_idiv(context, bden, g));
            b = f_mul(context, bnum, f_idiv(context, aden, g));
        }

        RubyInteger c = plus ? f_add(context, a, b) : f_sub(context, a, b);

        b = f_idiv(context, aden, g);
        g = f_gcd(context, c, g);
        newNum = f_idiv(context, c, g);
        a = f_idiv(context, bden, g);
        newDen = f_mul(context, a, b);
        
        return RubyRational.newRationalNoReduce(context, metaClass, newNum, newDen);
    }
    
    /** nurat_add */
    @JRubyMethod(name = "+")
    @Override
    public IRubyObject op_plus(ThreadContext context, IRubyObject other) {
        if (other instanceof RubyInteger) {
            return f_addsub(context, getMetaClass(), num, den, (RubyInteger) other, RubyFixnum.one(context.runtime), true);
        }
        if (other instanceof RubyFloat) {
            return f_add(context, r_to_f(context, this), other);
        }
        if (other instanceof RubyRational) {
            return op_plus(context, (RubyRational) other);
        }
        return coerceBin(context, sites(context).op_plus, other);
    }

    public final RubyNumeric op_plus(ThreadContext context, RubyRational other) {
        return f_addsub(context, getMetaClass(), num, den, other.num, other.den, true);
    }

    @Deprecated
    public IRubyObject op_add(ThreadContext context, IRubyObject other) { return op_plus(context, other); }

    /** nurat_sub */
    @JRubyMethod(name = "-")
    public IRubyObject op_minus(ThreadContext context, IRubyObject other) {
        if (other instanceof RubyInteger) {
            return f_addsub(context, getMetaClass(), num, den, (RubyInteger) other, RubyFixnum.one(context.runtime), false);
        }
        if (other instanceof RubyFloat) {
            return f_sub(context, r_to_f(context, this), other);
        }
        if (other instanceof RubyRational) {
            return op_minus(context, (RubyRational) other);
        }
        return coerceBin(context, sites(context).op_minus, other);
    }

    public final RubyNumeric op_minus(ThreadContext context, RubyRational other) {
        return f_addsub(context, getMetaClass(), num, den, other.num, other.den, false);
    }

    @Deprecated
    public IRubyObject op_sub(ThreadContext context, IRubyObject other) { return op_minus(context, other); }

    @Override
    public IRubyObject op_uminus(ThreadContext context) {
        return RubyRational.newRationalNoReduce(context, num.negate(context), den);
    }

    /** f_muldiv
     * 
     */
    private static RubyNumeric f_muldiv(ThreadContext context, RubyClass clazz,
                                        RubyInteger anum, RubyInteger aden,
                                        RubyInteger bnum, RubyInteger bden, final boolean mult) {
        if (!mult) {
            if (f_negative_p(context, bnum)) {
                anum = anum.negate(context);
                bnum = bnum.negate(context);
            }
            RubyInteger tmp = bnum; bnum = bden; bden = tmp;
        }
        
        final RubyInteger newNum, newDen;
        if (anum instanceof RubyFixnum anumf && aden instanceof RubyFixnum adenf &&
            bnum instanceof RubyFixnum bnumf && bden instanceof RubyFixnum bdenf) {
            long an = anumf.getValue();
            long ad = adenf.getValue();
            long bn = bnumf.getValue();
            long bd = bdenf.getValue();
            long g1 = i_gcd(an, bd);
            long g2 = i_gcd(ad, bn);
            
            newNum = f_imul(context, an / g1, bn / g2);
            newDen = f_imul(context, ad / g2, bd / g1);
        } else {
            RubyInteger g1 = f_gcd(context, anum, bden);
            RubyInteger g2 = f_gcd(context, aden, bnum);
            
            newNum = f_mul(context, f_idiv(context, anum, g1), f_idiv(context, bnum, g2));
            newDen = f_mul(context, f_idiv(context, aden, g2), f_idiv(context, bden, g1));
        }

        return RubyRational.newRationalNoReduce(context, clazz, newNum, newDen);
    }

    /** nurat_mul
     * 
     */
    @JRubyMethod(name = "*")
    public IRubyObject op_mul(ThreadContext context, IRubyObject other) {
        if (other instanceof RubyInteger) {
            return op_mul(context, (RubyInteger) other);
        }
        if (other instanceof RubyFloat) {
            return f_mul(context, r_to_f(context, this), other);
        }
        if (other instanceof RubyRational) {
            RubyRational otherRational = (RubyRational) other;
            return f_muldiv(context, getMetaClass(), num, den, otherRational.num, otherRational.den, true);
        }
        return coerceBin(context, sites(context).op_times, other);
    }

    public IRubyObject op_mul(ThreadContext context, RubyInteger other) {
        return f_muldiv(context, getMetaClass(), num, den, other, RubyFixnum.one(context.runtime), true);
    }

    /** nurat_div
     * 
     */
    @JRubyMethod(name = {"/", "quo"})
    public IRubyObject op_div(ThreadContext context, IRubyObject other) {
        if (other instanceof RubyInteger otherInteger) return op_div(context, otherInteger);
        if (other instanceof RubyFloat) {
            IRubyObject fval = r_to_f(context, this);
            return context.sites.Float.op_quo.call(context, fval, fval, other); // fval / other
        }
        if (other instanceof RubyRational otherRational) {
            if (otherRational.isZero(context)) throw context.runtime.newZeroDivisionError();

            return f_muldiv(context, getMetaClass(), num, den, otherRational.num, otherRational.den, false);
        }
        return coerceBin(context, sites(context).op_quo, other);
    }

    public final RubyNumeric op_div(ThreadContext context, RubyInteger other) {
        if (other.isZero(context)) throw context.runtime.newZeroDivisionError();

        return f_muldiv(context, getMetaClass(), num, den, other, asFixnum(context, 1), false);
    }

    /** nurat_fdiv
     * 
     */
    @Override
    @JRubyMethod(name = "fdiv")
    public IRubyObject fdiv(ThreadContext context, IRubyObject other) {
        return f_div(context, r_to_f(context, this), other);
    }

    /** nurat_expt
     * 
     */
    @JRubyMethod(name = "**")
    public IRubyObject op_expt(ThreadContext context, IRubyObject other) {
        if (k_exact_p(other) && f_zero_p(context, other)) {
            return RubyRational.newRationalBang(context, getMetaClass(), 1);
        }

        if (other instanceof RubyRational rat && rat.den.isOne(context)) other = rat.num;

        // Deal with special cases of 0**n and 1**n
        if (k_numeric_p(other) && k_exact_p(other)) {
            if (den.isOne(context)) {
                if (num.isOne(context)) {
                    return RubyRational.newRationalBang(context, getMetaClass(), 1);
                }
                if (f_minus_one_p(context, num) && k_integer_p(other)) {
                    return RubyRational.newRationalBang(context, getMetaClass(), f_odd_p(context, other) ? -1 : 1);
                }
                if (f_zero_p(context, num)) {
                    if (f_negative_p(context, other)) throw context.runtime.newZeroDivisionError();
                    return RubyRational.newRationalBang(context, getMetaClass(), 0);
                }
            }
        }

        // General case
        if (other instanceof RubyFixnum otherFixnum) {
            RubyNumeric num, den;

            if (otherFixnum.isPositiveNumber(context)) {
                num = (RubyNumeric) this.num.pow(context, other);
                den = (RubyNumeric) this.den.pow(context, other);
            } else if (otherFixnum.isNegativeNumber(context)) {
                var negate = otherFixnum.negate(context);
                num = (RubyNumeric) this.den.pow(context, negate);
                den = (RubyNumeric) this.num.pow(context, negate);
            } else {
                num = den = asFixnum(context, 1);
            }
            if (num instanceof RubyFloat) { /* infinity due to overflow */
                return den instanceof RubyFloat ? asFloat(context, Double.NaN) : num;
            } else if (den instanceof RubyFloat) { /* infinity due to overflow */
                num = asFixnum(context, 0);
                den = asFixnum(context, 1);
            }
            return newInstance(context, getMetaClass(), num, den);
        } else if (other instanceof RubyBignum) {
            throw argumentError(context, "exponent is too large");
        } else if (other instanceof RubyFloat || other instanceof RubyRational) {
            return f_expt(context, r_to_f(context, this), other);
        }
        return coerceBin(context, sites(context).op_exp, other);
    }

    public final IRubyObject op_expt(ThreadContext context, long other) {
        if (other == 0) return RubyRational.newRationalBang(context, getMetaClass(), 1);

        // Deal with special cases of 0**n and 1**n
        if (den.isOne(context)) {
            if (num.isOne(context)) return RubyRational.newRationalBang(context, getMetaClass(), 1);
            if (f_minus_one_p(context, num)) {
                return RubyRational.newRationalBang(context, getMetaClass(), other % 2 != 0 ? -1 : 1);
            }
            if (f_zero_p(context, num)) {
                if (other < 0) throw context.runtime.newZeroDivisionError();
                return RubyRational.newRationalBang(context, getMetaClass(), 0);
            }
        }

        // General case
        return fix_expt(context, asFixnum(context, other), Long.signum(other));
    }

    private RubyNumeric fix_expt(ThreadContext context, RubyInteger other, final int sign) {
        final RubyInteger tnum, tden;
        if (sign > 0) { // other > 0
            tnum = (RubyInteger) f_expt(context, num, other); // exp > 0
            tden = (RubyInteger) f_expt(context, den, other); // exp > 0
        } else if (sign < 0) { // other < 0
            RubyInteger otherNeg = other.negate(context);
            tnum = (RubyInteger) f_expt(context, den, otherNeg); // exp.negate > 0
            tden = (RubyInteger) f_expt(context, num, otherNeg); // exp.negate > 0
        } else { // other == 0
            tnum = tden = asFixnum(context, 1);
        }
        return RubyRational.newInstance(context, getMetaClass(), tnum, tden);
    }

    /** nurat_cmp
     * 
     */
    @JRubyMethod(name = "<=>")
    @Override
    public IRubyObject op_cmp(ThreadContext context, IRubyObject other) {
        if (other instanceof RubyFixnum || other instanceof RubyBignum) {
            if (den instanceof RubyFixnum && ((RubyFixnum) den).value == 1) return f_cmp(context, num, other);
            return f_cmp(context, this, RubyRational.newRationalBang(context, getMetaClass(), other));
        }
        if (other instanceof RubyFloat) {
            return f_cmp(context, r_to_f(context, this), other);
        }
        if (other instanceof RubyRational) {
            RubyRational otherRational = (RubyRational) other;
            final RubyInteger num1, num2;
            if (num instanceof RubyFixnum && den instanceof RubyFixnum &&
                otherRational.num instanceof RubyFixnum && otherRational.den instanceof RubyFixnum) {
                num1 = f_imul(context, ((RubyFixnum) num).value, ((RubyFixnum) otherRational.den).value);
                num2 = f_imul(context, ((RubyFixnum) otherRational.num).value, ((RubyFixnum) den).value);
            } else {
                num1 = f_mul(context, num, otherRational.den);
                num2 = f_mul(context, otherRational.num, den);
            }
            return f_cmp(context, f_sub(context, num1, num2), RubyFixnum.zero(context.runtime));
        }
        return coerceCmp(context, sites(context).op_cmp, other);
    }

    /** nurat_equal_p
     * 
     */
    @JRubyMethod(name = "==")
    @Override
    public IRubyObject op_equal(ThreadContext context, IRubyObject other) {
        if (other instanceof RubyFixnum || other instanceof RubyBignum) {
            return op_equal(context, (RubyInteger) other);
        }
        if (other instanceof RubyFloat) {
            return f_equal(context, r_to_f(context, this), other);
        }
        if (other instanceof RubyRational) {
            return op_equal(context, (RubyRational) other);
        }
        return f_equal(context, other, this);
    }

    public final IRubyObject op_equal(ThreadContext context, RubyInteger other) {
        if (num.isZero(context)) return asBoolean(context, other.isZero(context));
        if (!(den instanceof RubyFixnum fixnum) || fixnum.getValue() != 1) return context.fals;
        return f_equal(context, num, other);
    }

    final RubyBoolean op_equal(ThreadContext context, RubyRational other) {
        if (num.isZero(context)) return asBoolean(context, other.num.isZero(context));
        return asBoolean(context,
                f_equal(context, num, other.num).isTrue() && f_equal(context, den, other.den).isTrue());
    }

    @Override // "eql?"
    public IRubyObject eql_p(ThreadContext context, IRubyObject other) {
        if (!(other instanceof RubyRational)) return context.fals;
        return op_equal(context, (RubyRational) other);
    }

    /** nurat_coerce
     * 
     */
    @JRubyMethod(name = "coerce")
    public IRubyObject op_coerce(ThreadContext context, IRubyObject other) {
        if (other instanceof RubyFixnum || other instanceof RubyBignum) {
            return newArray(context, RubyRational.newRationalBang(context, getMetaClass(), other), this);
        } else if (other instanceof RubyFloat) {
            return newArray(context, other, r_to_f(context, this));
        } else if (other instanceof RubyRational) {
            return newArray(context, other, this);
        } else if (other instanceof RubyComplex otherComplex) {
            if (k_exact_p(otherComplex.getImage()) && f_zero_p(context, otherComplex.getImage())) {
                return newArray(context, RubyRational.newRationalBang(context, getMetaClass(), otherComplex.getReal()), this);
            } else {
                return newArray(context, other, RubyComplex.newComplexCanonicalize(context, this));
            }
        }

        throw typeError(context, str(context.runtime, other.getMetaClass(), " can't be coerced into ", getMetaClass()));
    }

    @Override
    public IRubyObject idiv(ThreadContext context, IRubyObject other) {
        if (toDouble(context, other) == 0.0) throw context.runtime.newZeroDivisionError();

        return f_floor(context, f_div(context, this, other));
    }

    public IRubyObject op_mod(ThreadContext context, IRubyObject other) {
        if (toDouble(context, other) == 0.0) throw context.runtime.newZeroDivisionError();

        return f_sub(context, this, f_mul(context, other, f_floor(context, f_div(context, this, other))));
    }

    /** nurat_divmod
     * 
     */
    @JRubyMethod(name = "divmod")
    public IRubyObject op_divmod(ThreadContext context, IRubyObject other) {
        if (toDouble(context, other) == 0.0) throw context.runtime.newZeroDivisionError();

        IRubyObject val = f_floor(context, f_div(context, this, other));
        return newArray(context, val, f_sub(context, this, f_mul(context, other, val)));
    }

    /** nurat_rem
     * 
     */
    @JRubyMethod(name = "remainder")
    public IRubyObject op_rem(ThreadContext context, IRubyObject other) {
        IRubyObject val = f_truncate(context, f_div(context, this, other));
        return f_sub(context, this, f_mul(context, other, val));
    }

    /** nurat_abs
     * 
     */
    @JRubyMethod(name = "abs")
    public IRubyObject op_abs(ThreadContext context) {
        if (!f_negative_p(context, this)) return this;
        return f_negate(context, this);
    }

    /**
     * MRI: nurat_floor_n
     */
    @JRubyMethod(name = "floor")
    public IRubyObject floor(ThreadContext context) {
        return roundCommon(context, null, RoundingMode.FLOOR);
    }

    @JRubyMethod(name = "floor")
    public IRubyObject floor(ThreadContext context, IRubyObject n) {
        return roundCommon(context, n, RoundingMode.FLOOR);
    }

    // MRI: nurat_floor
    private IRubyObject mriFloor(ThreadContext context) {
        return num.idiv(context, den);
    }

    /**
     * MRI: nurat_ceil_n
     */
    @Override
    @JRubyMethod(name = "ceil")
    public IRubyObject ceil(ThreadContext context) {
        return roundCommon(context, null, RoundingMode.CEILING);
    }

    @JRubyMethod(name = "ceil")
    public IRubyObject ceil(ThreadContext context, IRubyObject n) {
        return roundCommon(context, n, RoundingMode.CEILING);
    }

    // MRI: nurat_ceil
    private IRubyObject mriCeil(ThreadContext context) {
        return ((RubyInteger) ((RubyInteger) num.op_uminus(context)).idiv(context, den)).op_uminus(context);
    }

    @Override
    public RubyInteger convertToInteger() {
        return mriTruncate(metaClass.runtime.getCurrentContext());
    }

    @JRubyMethod(name = "to_i")
    public IRubyObject to_i(ThreadContext context) {
        return mriTruncate(context); // truncate(context);
    }


    @Override
    public BigInteger asBigInteger(ThreadContext context) {
        return convertToInteger().asBigInteger(context);
    }

    @Override
    @JRubyAPI
    public long asLong(ThreadContext context) {
        return convertToInteger().asLong(context);
    }

    /**
     * MRI: nurat_truncate
     */
    @JRubyMethod(name = "truncate")
    public IRubyObject truncate(ThreadContext context) {
        return roundCommon(context, null, RoundingMode.UNNECESSARY);
    }

    @JRubyMethod(name = "truncate")
    public IRubyObject truncate(ThreadContext context, IRubyObject n) {
        return roundCommon(context, n, RoundingMode.UNNECESSARY);
    }

    private RubyInteger mriTruncate(ThreadContext context) {
        if (num.isNegativeNumber(context)) {
            return ((RubyInteger) num.negate(context).idiv(context, den)).negate(context);
        }
        return (RubyInteger) num.idiv(context, den);
    }

    @JRubyMethod(name = "round")
    public IRubyObject round(ThreadContext context) {
        return roundCommon(context, null, RoundingMode.HALF_UP);
    }

    @JRubyMethod(name = "round")
    public IRubyObject round(ThreadContext context, IRubyObject n) {
        IRubyObject opts = ArgsUtil.getOptionsArg(context, n);
        if (opts != context.nil) n = null;

        return roundCommon(context, n, RubyNumeric.getRoundingMode(context, opts));
    }

    @JRubyMethod(name = "round")
    public IRubyObject round(ThreadContext context, IRubyObject n, IRubyObject opts) {
        opts = ArgsUtil.getOptionsArg(context, opts);

        return roundCommon(context, n, RubyNumeric.getRoundingMode(context, opts));
    }

    // MRI: f_round_common
    public IRubyObject roundCommon(ThreadContext context, final IRubyObject n, RoundingMode mode) {
        // case : precision arg is not given
        if (n == null) return doRound(context, mode);
        if (!(n instanceof RubyInteger nint)) throw typeError(context, "not an integer");

        final int nsign = nint.signum(context);
        RubyNumeric b = f_expt(context, asFixnum(context, 10), nint);
        IRubyObject s = nsign >= 0 ? op_mul(context, (RubyInteger) b) : op_mul(context, b); // (RubyRational) b

        if (s instanceof RubyFloat) return nsign < 0 ? RubyFixnum.zero(context.runtime) : this;

        var sr = s instanceof RubyRational rat ? rat : newRationalBang(context, getMetaClass(), s);
        var si = newRationalBang(context, getMetaClass(), sr.doRound(context, mode)).op_div(context, b);

        return si instanceof RubyRational r && f_cmp(context, nint, 1).value < 0 ? r.truncate(context) : si;
    }

    private IRubyObject doRound(ThreadContext context, RoundingMode mode) {
        return switch (mode) {
            case HALF_UP -> roundHalfUp(context);
            case HALF_EVEN -> roundHalfEven(context);
            case HALF_DOWN -> roundHalfDown(context);
            case FLOOR -> mriFloor(context);
            case CEILING -> mriCeil(context);
            case UNNECESSARY -> mriTruncate(context);
            default -> throw runtimeError(context, "BUG: invalid rounding mode: " + mode);
        };
    }

    // MRI: nurat_round_half_down
    private RubyInteger roundHalfDown(ThreadContext context) {
        RubyInteger num = this.num, den = this.den;
        final boolean neg = num.isNegativeNumber(context);

        if (neg) num = (RubyInteger) num.op_uminus(context);

        num = (RubyInteger) ((RubyInteger) num.op_mul(context, 2)).op_plus(context, den);
        num = (RubyInteger) num.op_minus(context, 1);
        den = (RubyInteger) den.op_mul(context, 2);
        num = (RubyInteger) num.idiv(context, den);

        if (neg) num = (RubyInteger) num.op_uminus(context);

        return num;
    }

    // MRI: nurat_round_half_even
    private RubyInteger roundHalfEven(ThreadContext context) {
        var num = this.num;
        var den = this.den;
        final boolean neg = num.isNegativeNumber(context);

        if (neg) num = (RubyInteger) num.op_uminus(context);

        num = (RubyInteger) ((RubyInteger) num.op_mul(context, 2)).op_plus(context, den);
        den = (RubyInteger) den.op_mul(context, 2);
        var qr = (RubyArray<?>) num.divmod(context, den);
        num = (RubyInteger) qr.eltOk(0);

        if (((RubyInteger) qr.eltOk(1)).isZero(context)) num = (RubyInteger) num.op_and(context, asFixnum(context, ~1L));
        if (neg) num = (RubyInteger) num.op_uminus(context);

        return num;
    }

    // MRI: nurat_round_half_up
    private RubyInteger roundHalfUp(ThreadContext context) {
        RubyInteger num = this.num, den = this.den;

        final boolean neg = num.isNegativeNumber(context);

        if (neg) num = (RubyInteger) num.op_uminus(context);

        num = (RubyInteger) ((RubyInteger) num.op_mul(context, 2)).op_plus(context, den);
        den = (RubyInteger) den.op_mul(context, 2);
        num = (RubyInteger) num.idiv(context, den);

        if (neg) num = (RubyInteger) num.op_uminus(context);

        return num;
    }

    /** nurat_to_f
     * 
     */

    @JRubyMethod(name = "to_f")
    public IRubyObject to_f(ThreadContext context) {
        return asFloat(context, asDouble(context));
    }

    @Override
    @JRubyAPI
    public double asDouble(ThreadContext context) {
        if (f_zero_p(context, num)) return 0;

        RubyInteger myNum = this.num;
        RubyInteger myDen = this.den;

        boolean minus = false;
        if (f_negative_p(context, myNum)) {
            myNum = f_negate(context, myNum);
            minus = true;
        }

        long nl = i_ilog2(context, myNum);
        long dl = i_ilog2(context, myDen);

        long ne = 0;
        if (nl > ML) {
            ne = nl - ML;
            myNum = myNum.op_rshift(context, ne);
        }

        long de = 0;
        if (dl > ML) {
            de = dl - ML;
            myDen = myDen.op_rshift(context, de);
        }

        long e = ne - de;

        if (e > 1023 || e < -1022) {
            warn(context, "out of Float range");
            return e > 0 ? Double.MAX_VALUE : 0;
        }

        double f = toDouble(context, myNum) / toDouble(context, myDen);

        if (minus) f = -f;

        f = ldexp(f, e);

        if (Double.isInfinite(f) || Double.isNaN(f)) warn(context, "out of Float range");

        return f;
    }

    private static final long ML = (long)(Math.log(Double.MAX_VALUE) / Math.log(2.0) - 1);

    /**
     * @param context
     * @return
     * @deprecated USe {@link org.jruby.RubyRational#asDouble(ThreadContext)} instead.
     */
    @Deprecated(since = "10.0")
    public double getDoubleValue(ThreadContext context) {
        return asDouble(context);
    }

    /** nurat_to_r
     * 
     */
    @JRubyMethod(name = "to_r")
    public IRubyObject to_r(ThreadContext context) {
        return this;
    }

    /** nurat_rationalize
     *
     */
    @JRubyMethod(name = "rationalize", optional = 1, checkArity = false)
    public IRubyObject rationalize(ThreadContext context, IRubyObject[] args) {
        int argc = Arity.checkArgumentCount(context, args, 0, 1);

        IRubyObject a, b;

        if (argc == 0) return to_r(context);

        if (f_negative_p(context, this)) {
            return f_negate(context, ((RubyRational) f_abs(context, this)).rationalize(context, args));
        }

        IRubyObject eps = f_abs(context, args[0]);
        a = f_sub(context, this, eps);
        b = f_add(context, this, eps);

        if (f_equal(context, a, b).isTrue()) return this;

        IRubyObject[] ans = nurat_rationalize_internal(context, a, b);

        return newInstance(context, this.metaClass, (RubyInteger) ans[0], (RubyInteger) ans[1]);
    }

    /** nurat_hash
     * 
     */
    @JRubyMethod(name = "hash")
    public RubyFixnum hash(ThreadContext context) {
        return (RubyFixnum) f_xor(context,
                (RubyInteger) invokedynamic(context, num, HASH),
                (RubyInteger) invokedynamic(context, den, HASH));
    }

    @Override
    public int hashCode() {
        return num.hashCode() ^ den.hashCode();
    }

    /** nurat_to_s
     * 
     */
    @Override
    @JRubyMethod(name = "to_s")
    public RubyString to_s(ThreadContext context) {
        RubyString str = newString(context, new ByteList(10), USASCIIEncoding.INSTANCE);
        return str.append(num.to_s(context)).cat((byte)'/').append(den.to_s(context));
    }

    /** nurat_inspect
     * 
     */
    @JRubyMethod(name = "inspect")
    public RubyString inspect(ThreadContext context) {
        RubyString str = newString(context, new ByteList(12), USASCIIEncoding.INSTANCE);
        str.cat((byte)'(');
        str.append((RubyString) num.inspect(context));
        str.cat((byte)'/');
        str.append((RubyString) den.inspect(context));
        str.cat((byte)')');
        return str;
    }

    /** nurat_marshal_dump
     * 
     */
    @JRubyMethod(name = "marshal_dump", visibility = Visibility.PRIVATE)
    public IRubyObject marshal_dump(ThreadContext context) {
        var dump = newArray(context, num, den);
        if (hasVariables()) dump.syncVariables(this);
        return dump;
    }

    /** nurat_marshal_load
     * 
     */
    @JRubyMethod(name = "marshal_load")
    public IRubyObject marshal_load(ThreadContext context, IRubyObject arg) {
        checkFrozen();
        RubyArray load = arg.convertToArray();
        IRubyObject num = load.size() > 0 ? load.eltInternal(0) : context.nil;
        IRubyObject den = load.size() > 1 ? load.eltInternal(1) : context.nil;

        // MRI: nurat_canonicalize, negation part
        if (den != context.nil && canonicalizeShouldNegate(context, den.convertToInteger())) {
            num = f_negate(context, num);
            den = f_negate(context, den);
        }
        intCheck(context, num);
        intCheck(context, den);

        this.num = (RubyInteger) num;
        this.den = (RubyInteger) den;

        if (load.hasVariables()) syncVariables((IRubyObject)load);
        return this;
    }

    private static final ObjectMarshal RATIONAL_MARSHAL = new ObjectMarshal() {
        @Override
        @Deprecated(since = "10.0", forRemoval = true)
        @SuppressWarnings("removal")
        public void marshalTo(Ruby runtime, Object obj, RubyClass type, org.jruby.runtime.marshal.MarshalStream marshalStream) {
            throw typeError(runtime.getCurrentContext(), "marshal_dump should be used instead for Rational");
        }
        @Override
        public void marshalTo(ThreadContext context, RubyOutputStream out, Object obj, RubyClass type, MarshalDumper marshalStream) {
            throw typeError(context, "marshal_dump should be used instead for Rational");
        }

        @Override
        @Deprecated(since = "10.0", forRemoval = true)
        @SuppressWarnings("removal")
        public Object unmarshalFrom(Ruby runtime, RubyClass type,
                                    org.jruby.runtime.marshal.UnmarshalStream unmarshalStream) throws IOException {
            ThreadContext context = runtime.getCurrentContext();

            RubyRational r = (RubyRational) RubyClass.DEFAULT_OBJECT_MARSHAL.unmarshalFrom(runtime, type, unmarshalStream);

            RubyInteger num = intCheck(context, r.removeInstanceVariable("@numerator"));
            RubyInteger den = intCheck(context, r.removeInstanceVariable("@denominator"));

            // MRI: nurat_canonicalize, negation part
            if (canonicalizeShouldNegate(context, den)) {
                num = num.negate(context);
                den = den.negate(context);
            }

            r.num = num;
            r.den = den;

            return r;
        }

        @Override
        public Object unmarshalFrom(ThreadContext context, RubyInputStream in, RubyClass type, MarshalLoader loader) {
            RubyRational r = (RubyRational) RubyClass.DEFAULT_OBJECT_MARSHAL.unmarshalFrom(context, in, type, loader);

            RubyInteger num = intCheck(context, r.removeInstanceVariable("@numerator"));
            RubyInteger den = intCheck(context, r.removeInstanceVariable("@denominator"));

            // MRI: nurat_canonicalize, negation part
            if (canonicalizeShouldNegate(context, den)) {
                num = num.negate(context);
                den = den.negate(context);
            }

            r.num = num;
            r.den = den;

            return r;
        }
    };

    static IRubyObject[] str_to_r_internal(final ThreadContext context, final RubyString str, boolean raise) {
        str.verifyAsciiCompatible();

        final IRubyObject nil = context.nil;
        ByteList bytes = str.getByteList();

        if (bytes.getRealSize() == 0) return new IRubyObject[] { nil, str };

        IRubyObject m;
        try {
            m = RubyRegexp.newDummyRegexp(context.runtime, Numeric.RationalPatterns.rat_pat).match_m(context, str, false);
        } catch(RaiseException re) {
            context.setErrorInfo(context.nil);
            return new IRubyObject[]{context.nil};
        }
        if (m != nil) {
            RubyMatchData match = (RubyMatchData) m;
            IRubyObject si = match.at(context, 1);
            RubyString nu = (RubyString) match.at(context, 2);
            IRubyObject de = match.at(context, 3);
            IRubyObject re = match.post_match(context);
            
            var a = nu.split(context, RubyRegexp.newDummyRegexp(context.runtime, Numeric.RationalPatterns.an_e_pat));
            RubyString ifp = (RubyString)a.eltInternal(0);
            IRubyObject exp = a.size() != 2 ? nil : a.eltInternal(1);
            
            a = ifp.split(context, newString(context, "."));
            IRubyObject ip = a.eltInternal(0);
            IRubyObject fp = a.size() != 2 ? nil : a.eltInternal(1);
            
            IRubyObject v = RubyRational.newRationalCanonicalize(context, (RubyInteger) f_to_i(context, ip));
            
            if (fp != nil) {
                bytes = fp.convertToString().getByteList();
                int count = 0;
                byte[] buf = bytes.getUnsafeBytes();
                int i = bytes.getBegin();
                int end = i + bytes.getRealSize();

                while (i < end) {
                    if (ASCIIEncoding.INSTANCE.isDigit(buf[i])) count++;
                    i++;
                }

                RubyInteger l = (RubyInteger) asFixnum(context, 10).op_pow(context, count);
                v = f_mul(context, v, l);
                v = f_add(context, v, f_to_i(context, fp));
                v = f_div(context, v, l);
            }

            if (si != nil) {
                ByteList siBytes = si.convertToString().getByteList();
                if (!siBytes.isEmpty() && siBytes.get(0) == '-') v = f_negate(context, v);
            }

            if (exp != nil) {
                IRubyObject denExp = f_to_i(context, exp);
                if (denExp instanceof RubyFixnum) {
                    v = f_mul(context, v, f_expt(context, asFixnum(context, 10), denExp));
                } else if (f_negative_p(context, denExp)) {
                    v = asFloat(context, 0.0);
                } else {
                    v = asFloat(context, Double.POSITIVE_INFINITY);
                }
            }

            if (de != nil) {
                IRubyObject denominator = f_to_r(context, de);
                if (!raise && f_zero_p(context, denominator)) {
                    return new IRubyObject[] { nil, str };
                }
                v = f_div(context, v, denominator);
            }
            return new IRubyObject[] { v, re };
        }
        return new IRubyObject[] { nil, str };
    }
    
    private static IRubyObject str_to_r_strict(ThreadContext context, RubyString str, boolean raise) {
        IRubyObject[] ary = str_to_r_internal(context, str, raise);
        if (ary[0] == context.nil || ary[1].convertToString().getByteList().length() > 0) {
            if (raise) throw argumentError(context, "invalid value for convert(): " + str.inspect(context));

            return context.nil;
        }

        return ary[0]; // (RubyRational)
    }

    /**
     * rb_numeric_quo
     */
    public static IRubyObject numericQuo(ThreadContext context, IRubyObject x, IRubyObject y) {
        if (x instanceof RubyComplex c) {
            return c.op_div(context, y);
        }

        if (y instanceof RubyFloat) return ((RubyNumeric)x).fdiv(context, y);

        x = TypeConverter.convertToType(x, context.runtime.getRational(), "to_r");
        return ((RubyRational) x).op_div(context, y);
    }

    @Deprecated
    public IRubyObject op_floor(ThreadContext context) {
        return floor(context);
    }

    @Deprecated
    public IRubyObject op_floor(ThreadContext context, IRubyObject n) {
        return floor(context, n);
    }

    @Deprecated
    public IRubyObject op_ceil(ThreadContext context) {
        return ceil(context);
    }

    @Deprecated
    public IRubyObject op_ceil(ThreadContext context, IRubyObject n) {
        return ceil(context, n);
    }

    @Deprecated
    public IRubyObject op_idiv(ThreadContext context, IRubyObject other) {
        return idiv(context, other);
    }

    @Deprecated
    public IRubyObject op_fdiv(ThreadContext context, IRubyObject other) {
        return fdiv(context, other);
    }

    private static JavaSites.RationalSites sites(ThreadContext context) {
        return context.sites.Rational;
    }

    private static IRubyObject r_to_f(ThreadContext context, RubyRational r) {
        return sites(context).to_f.call(context, r, r); 
    }
}
