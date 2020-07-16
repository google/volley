package com.android.volley.toolbox;

import java.io.File;

/** Represents a supplier for {@link File}s. */
public interface FileSupplier {
    File get();
}
