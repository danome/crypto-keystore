(ns crypto.keystore
  "Functions for creating and managing Java Keystores."
  (:require [clojure.java.io :as io])
  (:import java.io.StringReader
           java.io.StringWriter
           java.security.KeyStore
           java.security.KeyStore$PrivateKeyEntry
           java.security.KeyPair
           java.security.PrivateKey
           java.security.Security
           java.security.cert.Certificate
           org.bouncycastle.openssl.PEMReader
           org.bouncycastle.openssl.PEMWriter
           org.bouncycastle.openssl.PasswordFinder
           org.bouncycastle.jce.provider.BouncyCastleProvider))

(Security/addProvider (BouncyCastleProvider.))

(deftype PasswordFn [password-fn]
  PasswordFinder
  (getPassword [_]
    (.toCharArray (password-fn))))

(defprotocol PemSource
  (make-pem-reader [source pass-fn]))

(extend-protocol PemSource
  String
  (make-pem-reader [s pass-fn] (make-pem-reader (StringReader. s) pass-fn))
  java.io.Reader
  (make-pem-reader [r pass-fn] (PEMReader. r (PasswordFn. pass-fn)))
  Object
  (make-pem-reader [x pass-fn] (make-pem-reader (io/reader x) pass-fn)))

(defn pem-reader
  "Create a PEMReader from a PEM source and optional password function."
  ([source]
     (make-pem-reader source (constantly nil)))
  ([source password-fn]
     (make-pem-reader source password-fn)))

(defn pem-seq
  "Return a lazy seq of objects from a PEMReader."
  [reader]
  (take-while
   (complement nil?)
   (repeatedly #(.readObject reader))))

(defn import-cert
  "Import a PEM certificate file into the keystore."
  [keystore alias cert]
  (with-open [r (pem-reader cert)]
    (doseq [c (pem-seq r) :when (instance? Certificate c)]
      (.setCertificateEntry keystore alias c))))

(defn pem-writer
  "Create a PEMWriter for an output stream, reader or file."
  [output]
  (PEMWriter. (io/writer output)))

(defn- write-str [f]
  (let [sw (StringWriter.)]
    (f sw)
    (.toString sw)))

(defn export-cert
  "Export a certificate in a keystore encoded in PEM format. If an output is supplied,
  write to it directly, otherwise return a string."
  ([keystore alias]
     (write-str (partial export-cert keystore alias)))
  ([keystore alias output]
     (with-open [w (pem-writer output)]
       (.writeObject w (.getCertificate keystore alias)))))

(defn keystore
  "Create a blank KeyStore."
  ([]
     (keystore (KeyStore/getDefaultType)))
  ([type]
     (doto (KeyStore/getInstance type)
       (.load nil))))