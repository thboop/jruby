exclude :test_load_ospath, "we lose encoding on the way into load, can't preserve for LoadError message"
exclude :test_loading_fifo_fd_leak, "elaborate test to force FD starvation, needs investigation"
exclude :test_loading_fifo_threading_raise, "we do not use IO to read files and so will not show as stopped while loading"
exclude :test_loading_fifo_threading_success, "we do not use IO to read files and so will not show as stopped while loading"
exclude :test_private_in_wrapped_load, "needs investigation"
exclude :test_provide_in_required_file, "work in progress"
exclude :test_public_in_wrapped_load, "needs investigation"
exclude :test_require_changed_home, "needs investigation"
exclude :test_require_nonascii, "needs investigation"
exclude :test_require_nonascii_path, "work in progress"
exclude :test_require_nonascii_path_shift_jis, "needs investigation"
exclude :test_require_path_home_1, "needs investigation"
exclude :test_require_path_home_2, "needs investigation"
exclude :test_require_path_home_3, "needs investigation"
exclude :test_require_too_long_filename, "needs investigation"
exclude :test_require_with_loaded_features_pop, "needs investigation"
exclude :test_resolve_feature_path, "work in progress"
