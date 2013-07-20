(ns circle.wait-for-test
  (:require [bond.james :as bond]
            [clj-time.core :as time]
            [midje.sweet :refer :all]
            [slingshot.slingshot :refer (try+ throw+)]
            [circle.wait-for :refer :all]
            [circle.util.stateful-fn :refer (stateful-fn)])
  (:import java.io.IOException))

(defn foo []
  (println "foo"))

(defn bar []
  (println "bar"))

(fact "catch-dispatch works"
  (catch-dispatch {} (IOException.)) => :default
  (catch-dispatch {:catch [IOException]} (IOException.)) => :seq-throwables
  (catch-dispatch {:catch :foo} {:foo false}) => :slingshot-keyword
  (catch-dispatch {:catch [:foo :bar]} {:foo true}) => :slingshot-vector
  (catch-dispatch {:catch (fn [t] t)} (IOException.)) => :fn
  (catch-dispatch {:catch (fn [t] t)} {:foo true}) => :fn)

(fact "catch? works"
  (catch? {} (IOException.)) => false
  (catch? {:catch [IOException]} (IOException.)) => true
  (catch? {:catch :foo} {:foo true}) => true
  (catch? {:catch :foo} {:bar true}) => falsey
  (catch? {:catch [:foo :bar]} {}) => falsey
  (catch? {:catch [:foo :bar]} {:foo true}) => falsey
  (catch? {:catch [:foo :bar]} {:foo :bar}) => true
  (catch? {:catch (fn [t]
                    (:foo t))} {:foo :bar}) => :bar)

(fact "wait-for retries"
  (wait-for {:sleep (time/millis 1)
             :tries 10}
            (fn []
              (foo))) => true
  (provided
    (foo) =streams=> [false false false false true] :times 5))

(fact "wait-for throws on timeout"
  (wait-for {:sleep (time/millis 1)
             :tries 10}
            (fn []
              (foo))) => (throws Exception)
  (provided
    (foo) => false :times 10))

(fact "wait-for works with an error hook"
  (wait-for {:sleep (time/millis 1)
             :tries 10
             :error-hook (fn [e]
                           (printf (format "caught: %s\n" (.getMessage e))))}
            (fn []
              (foo))) => true
   (provided
     (foo) =streams=> [false false false false true] :times 5))

(fact "wait-for works with a success fn"
  (wait-for {:sleep (time/millis 1)
             :tries 10
             :success-fn (fn [v]
                           (= v 8))}
            (fn []
              (foo))) => 8
   (provided
     (foo) =streams=> [0 1 2 3 4 5 6 7 8 9 10] :times 9))

(fact "timeout works"
  (wait-for {:sleep (time/millis 1)
             :tries 10
             :timeout (time/millis 400)
             :success-fn (fn [v]
                           (= v 42))}
            (fn []
              (Thread/sleep 100)
              (foo))) => (throws Exception)
   (provided
     (foo) =streams=> [0 1 2 3 4 5 6 7 8 9 10] :times 4))

(fact "throws when f fails to become ready"
  (fact "timeout works"
  (wait-for {:sleep (time/millis 1)
             :timeout (time/millis 20)}
            (fn []
              (foo))) => (throws Exception)
   (provided
     (foo) => false)))

(fact "wait-for does not catch exceptions by default"
  (with-redefs [foo (fn []
                      (throw (Exception. "fail!")))]
    (bond/with-spy [foo]
      (try
        (wait-for {:tries 5
                   :sleep (time/millis 1)}
                  foo)
        false
        (catch Exception e
          true)) => true
          (-> foo bond/calls count) => 1)))


(fact "wait-for does not catch slingshots by default"
  (with-redefs [foo (fn []
                      (throw+ {:test :test}))]
    (bond/with-spy [foo]
      (try+
       (wait-for {:sleep (time/millis 1)
                  :tries 5}
                 foo)
       false
       (catch Object _
         true)) => true
         (-> foo bond/calls count) => 1)))

(fact "retries on exception"
  (wait-for {:tries 4
             :sleep (time/millis 1)
             :catch [IOException]}
            (stateful-fn (throw (IOException.))
                         (throw (IOException.))
                         (throw (IOException.))
                         42)) => 42)

(fact "catches listed exceptions"
  (wait-for {:tries 4
             :sleep (time/millis 1)
             :catch [IOException]}
            (stateful-fn (throw (ArrayIndexOutOfBoundsException.)))) => (throws ArrayIndexOutOfBoundsException))

(fact "supports error-hook"
  (wait-for {:tries 4
             :sleep (time/millis 1)
             :catch [IOException]
             :error-hook (fn [e] (foo))}
            (fn []
              (throw (IOException.)))) => (throws IOException)
  (provided
    (foo) => anything :times 4))

(fact "supports unlimited retries"
  (wait-for {:tries :unlimited
             :sleep (time/millis 1)
             :timeout (time/millis 100)}
            (fn []
              (foo))) => (throws Exception)
  (provided
    (foo) => false :times #(> % 40)))

(fact "supports nil sleep"
  (wait-for {:tries 3
             :sleep nil}
            (fn []
              (foo))) => (throws Exception)
  (provided
    (foo) => false :times 3))

(fact "supports default sleep value"
  (wait-for {:tries 2}
            (fn []
              (foo))) => (throws Exception)
  (provided
    (foo) => false :times 2))

(fact ":no-throw works"
  (wait-for {:tries 3
             :sleep nil
             :catch [IOException]
             :error-hook (fn [e] (foo))
             :success-fn :no-throw}
            (stateful-fn (throw (IOException.))
                         (throw (IOException.))
                         nil)) => nil
  (provided
    (foo) => anything :times 2))

(fact "tries not used when sleep and timeout are specified"
  (wait-for {:sleep (time/millis 1)
             :timeout (time/millis 500)}
            foo) => (throws Exception)
  (provided
    (foo) => false :times #(> % 100)))

(fact "throws when success-fn isn't a fn"
  (bond/with-spy [foo]
    (wait-for {:success-fn false
               :sleep (time/millis 1)
               :tries 5}
              #(foo)) => (throws Exception)
    (-> foo bond/calls count) => 0))

(fact ":catch works with keywords"
  (with-redefs [foo (stateful-fn
                     (throw+ {:foo true :bar false})
                     true)]
    (bond/with-spy [foo]
      (wait-for {:sleep (time/millis 1)
                 :catch :foo
                 :tries 5}
                #(foo)) => true
                (-> foo bond/calls count) => 2)))

(fact ":catch works with vectors"
  (with-redefs [foo (stateful-fn
                     (throw+ {:foo :bar})
                     true)]
    (bond/with-spy [foo]
      (wait-for {:sleep (time/millis 1)
                 :catch [:foo :bar]
                 :tries 5}
                #(foo)) => true
                (-> foo bond/calls count) => 2)))

(fact "wait-for when catch is a function"
  (with-redefs [foo (fn []
                      (throw (Exception. "test")))]
    (bond/with-spy [foo]
      (wait-for {:catch (fn [t] false)
                 :sleep (time/millis 1)
                 :tries 5}
                foo) => (throws Exception)
      (-> foo bond/calls count) => 1)))

(fact "wait-for when catch is a function part 2, success"
  (with-redefs [foo (fn []
                      (throw (Exception. "test")))]
    (bond/with-spy [foo]
      (wait-for {:catch (stateful-fn true true false)
                 :sleep (time/millis 1)
                 :tries 5}
                foo) => (throws Exception)
      (-> foo bond/calls count) => 3)))
