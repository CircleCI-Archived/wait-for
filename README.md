wait-for
========

[circle/wait-for "1.0.0]

A HOF that provides retrying.

Let's start with a simple example.

```clojure
(:require [circle.wait-for :refer (wait-for)])

(wait-for #(unreliable-fn :foo :bar))

```
This calls unreliable-fn, up to 3 times, returning as soon as unreliable-fn returns truthy.

Options
-------
wait-for has two signatures:
`(wait-for f)` and `(wait-for options f)`. Options is a map. f is always a fn of no arguments.

Terminating
-----------

By default, wait-for terminates if the function passed in returns truthy. It returns the successful value, or throws if f never returned truthy.

The number of retries can be specified with the options :tries, :sleep and :timeout. See the docstring for more.

If you want to wait for a specific return value, use the :success-fn option.

Exceptions
----------

By default, wait-for does not catch exceptions, but can be configured to do so:

```clojure
(wait-for {:catch [java.net.SocketTimeoutException]} #(foo name))
```

The :catch clause can take numerous options, as seen in the docstring.

Inspired by [https://github.com/joegallo/robert-bruce Robert Bruce]
