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
 * Copyright (C) 2001-2020 JRuby Contributors
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

package org.jruby.ast.java_signature;

/**
 * Visitor interface to be implemented by visitors of the Annotation AST.
 * each node will call the visit method appropriate to its type.
 * @see org.jruby.ast.java_signature.Annotation
 *
 * @param <T> the type
 * @author Patrick Plenefisch
 **/
public interface AnnotationVisitor<T> {
	T annotation(Annotation annotation);
	T annotation_array(ArrayAnnotationExpression arrayAnnotationExpression);
	/**
	 * For all literals that don't need extra type processing or parsing (String, bool)
	 */
	T literal(Literal literal);
	T number_literal(NumberLiteral numberLiteral);
	T char_literal(CharacterLiteral characterLiteral);
	T type(TypeNode typeNode);
}
