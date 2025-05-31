(ns hooks.defnc
  (:require [clj-kondo.hooks-api :as api]))

(defn defnc-common [{:keys [node]} private?]
  (let [children (:children node)
        [name deps args & body] (rest children)]
    (when (and name deps args)
      (let [ctx-sym (api/token-node '$ctx)  ; Changed from 'ctx to '$ctx
            dep-names (:children deps)
            let-bindings (mapcat (fn [dep]
                                   [dep 
                                    (api/list-node
                                     [(api/token-node (keyword (:value dep)))
                                      ctx-sym])])
                                 dep-names)
            def-fn (if private? 'defn- 'defn)
            new-node (api/list-node
                      [(api/token-node def-fn)
                       name
                       (api/vector-node (cons ctx-sym (:children args)))
                       (api/list-node
                        (list* (api/token-node 'let)
                               (api/vector-node let-bindings)
                               body))])]
        {:node new-node}))))

(defn defnc- [node]
  (defnc-common node true))

(defn defnc [node]
  (defnc-common node false))

(defn with-context [{:keys [node]}]
  (let [[ctx-expr deps & body] (rest (:children node))
        dep-names (:children deps)
        let-bindings (mapcat (fn [dep]
                               [dep 
                                (api/list-node
                                 [(api/token-node (keyword (:value dep)))
                                  ctx-expr])])
                             dep-names)]
    {:node (api/list-node
            (list* (api/token-node 'let)
                   (api/vector-node let-bindings)
                   body))}))