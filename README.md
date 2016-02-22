# clj-karabiner [![Continuous Integration status](https://secure.travis-ci.org/olivermg/clj-karabiner.png)](http://travis-ci.org/olivermg/clj-karabiner) [![Clojars Project](https://img.shields.io/clojars/v/clj-karabiner.svg)](https://clojars.org/clj-karabiner)

[![Clojars Project](https://clojars.org/clj-karabiner/latest-version.svg)](https://clojars.org/clj-karabiner)

A Clojure library that provides some custom data structures that I found to be helpful.

Sure, you can build your own data structures on top of maps, vectors etc., but in many cases that
means you also have to provide custom functions like my=, my-assoc, my-dissoc, my-merge etc.

The custom data structures here provide special features but still allow you to make use
of Clojure's native functions (as long as this makes sense).


## Data Types

The following Data Types exist:

* **relevancemap**:
  A map that lets you define which of its properties should influence its hash value.

* **transactionmap**:
  A map that keeps track of changes and provides means by which you can commit or
  roll back these changes.

* **lazyvector**:
  A vector that treats its contents lazily. It will only `deref` these contents when
  necessary.

* **lazymap**:
  A map that treats its contents lazily. It will only `deref` its contents when necessary.

* **dbmap**:
  A map that combines **relevancemap**, **transactionmap** and **lazyvector** into one single
  type that is suitable for representing database records. Additionally, it makes a distinction
  between the entity's own properties vs. references to other entities.


## Criticizm appreciated

Some features of these custom datatypes may not (yet) be consistent with Clojure's paradigm of
how it handles data types.

However, I'll try to minimize the discrepancies, if there are any. I'll be very thankful for
any kind of feedback, hints and tips for how to make this library better! If you find
there are issues that should be improved, fixed or should not be done at all, just drop
me a line - I appreciate that.


## Usage

Still alpha! Examples will follow.


## License

Distributed under the MIT License (MIT).

Copyright © 2016 Oliver Wegner

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
