exclude :test_extend_string, "attempts to instance_eval a frozen String, which breaks because we create singleton class (https://github.com/jruby/jruby/issues/8638)"
exclude :test_hash_subclass_extend, "work in progress"
exclude :test_object_prepend, "work in progress"
exclude :test_singleton, "work in progress"
exclude :test_string_subclass, "attempts to instance_eval a frozen String, which breaks because we create singleton class (https://github.com/jruby/jruby/issues/8638)"
exclude :test_string_subclass_cycle, "attempts to instance_eval a frozen String, which breaks because we create singleton class (https://github.com/jruby/jruby/issues/8638)"
exclude :test_string_subclass_cycle, "work in progress"
exclude :test_string_subclass_extend, "attempts to instance_eval a frozen String, which breaks because we create singleton class (https://github.com/jruby/jruby/issues/8638)"
