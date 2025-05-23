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
 * Copyright (C) 2008 JRuby project
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

package org.jruby.ext.ffi.io;

import jnr.posix.FileStat;
import org.jruby.Ruby;
import org.jruby.RubyClass;
import org.jruby.RubyIO;
import org.jruby.RubyModule;
import org.jruby.RubyNumeric;
import org.jruby.anno.JRubyClass;
import org.jruby.anno.JRubyMethod;
import org.jruby.api.Access;
import org.jruby.runtime.ThreadContext;
import org.jruby.runtime.builtin.IRubyObject;
import org.jruby.util.io.ModeFlags;

import java.nio.channels.ByteChannel;

import static org.jruby.api.Convert.toInt;

/**
 * An IO implementation that reads/writes to a native file descriptor.
 */
@JRubyClass(name="FFI::" + FileDescriptorIO.CLASS_NAME, parent="IO")
public class FileDescriptorIO extends RubyIO {
    public static final String CLASS_NAME = "FileDescriptorIO";

    public FileDescriptorIO(Ruby runtime, RubyClass klass) {
        super(runtime, klass);
        MakeOpenFile();
    }

    public FileDescriptorIO(Ruby runtime, IRubyObject fd) {
        this(runtime.getCurrentContext(), fd);
    }

    public FileDescriptorIO(ThreadContext context, IRubyObject fd) {
        super(context.runtime, Access.getClass(context, "FFI", CLASS_NAME));
        MakeOpenFile();
        ModeFlags modes = newModeFlags(context.runtime, ModeFlags.RDWR);
        int fileno = toInt(context, fd);
        FileStat stat = context.runtime.getPosix().fstat(fileno);
        ByteChannel channel;

        if (stat.isSocket()) {
            channel = new jnr.enxio.channels.NativeSocketChannel(fileno);
        } else if (stat.isBlockDev() || stat.isCharDev()) {
            channel = new jnr.enxio.channels.NativeDeviceChannel(fileno);
        } else {
            channel = new FileDescriptorByteChannel(context.runtime, fileno);
        }

//        openFile.setMainStream(ChannelStream.open(getRuntime(), new ChannelDescriptor(channel, modes, FileDescriptorHelper.wrap(fileno))));
        openFile.setChannel(channel);
        openFile.setMode(modes.getOpenFileFlags());
        openFile.setMode(modes.getOpenFileFlags());
        openFile.setSync(true);
    }

    public static RubyClass createFileDescriptorIOClass(ThreadContext context, RubyModule FFI, RubyClass IO) {
        return FFI.defineClassUnder(context, CLASS_NAME, IO, FileDescriptorIO::new).
                defineMethods(context, FileDescriptorIO.class).
                defineConstants(context, FileDescriptorIO.class);
    }

    @JRubyMethod(name = "new", meta = true)
    public static FileDescriptorIO newInstance(ThreadContext context, IRubyObject recv, IRubyObject fd) {
        return new FileDescriptorIO(context, fd);
    }

    @JRubyMethod(name = "wrap", meta = true)
    public static RubyIO wrap(ThreadContext context, IRubyObject recv, IRubyObject fd) {
        return new FileDescriptorIO(context, fd);
    }
}
