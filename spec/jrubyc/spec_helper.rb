require_relative '../java_integration/spec_helper'

require 'jruby'
require 'jruby/compiler'

def javac_compile_contents(string, filename)
  require 'tmpdir' ; Dir.mktmpdir('jrubyc') do |tmpdir|
    file_path = File.join(tmpdir, filename)
    File.open(file_path, 'w') { |f| f << string }

    compiler = javax.tools.ToolProvider.getSystemJavaCompiler
    fmanager = compiler.getStandardFileManager(nil, nil, nil)
    modules = ["-p", ENV_JAVA["jdk.module.path"],]
    units = fmanager.getJavaFileObjectsFromStrings( [ file_path ] )
    compilation_task = compiler.getTask(nil, fmanager, nil, modules, nil, units)
    # this will need to be updated to org.jruby.base once the dist is fully modularized
    compilation_task.addModules(["org.jruby.dist"])
    compilation_task.call # returns boolean
  end
end