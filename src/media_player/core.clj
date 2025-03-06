(ns media-player.core
  (:gen-class)
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [seesaw.core :as s]
            [seesaw.bind :as b]
            [seesaw.font :as f]
            [seesaw.chooser :as chooser]))

;; Application state (using an atom for reactive updates)
(def app-state (atom {:current-file nil
                      :player nil
                      :playing? false
                      :playlist []}))

;; Audio backends
(defprotocol AudioPlayer
  (play-file [this file-path])
  (stop [this])
  (cleanup [this]))

;; MP3 Player implementation
(defrecord MP3Player []
  AudioPlayer
  (play-file [this file-path]
    (let [player (javazoom.jl.player.Player. (io/input-stream file-path))
          play-thread (future
                        (try
                          (.play player)
                          (swap! app-state assoc :playing? false)
                          (catch Exception e
                            (println "Error playing MP3:" (.getMessage e)))))]
      (swap! app-state assoc
             :player {:instance player
                      :thread play-thread
                      :type :mp3}
             :playing? true
             :current-file file-path)))

  (stop [this]
    (when-let [player (:instance (:player @app-state))]
      (future-cancel (:thread (:player @app-state)))
      (.close player)
      (swap! app-state assoc :playing? false :player nil)))

  (cleanup [this]
    (stop this)))

;; Other audio formats player
(defrecord AudioClipPlayer []
  AudioPlayer
  (play-file [this file-path]
    (let [audio-input (javax.sound.sampled.AudioSystem/getAudioInputStream
                       (io/file file-path))
          clip (javax.sound.sampled.AudioSystem/getClip)]
      (.open clip audio-input)
      (.addLineListener clip
                        (reify javax.sound.sampled.LineListener
                          (update [this event]
                            (when (= (.getType event)
                                     javax.sound.sampled.LineEvent$Type/STOP)
                              (swap! app-state assoc :playing? false)))))
      (.start clip)
      (swap! app-state assoc
             :player {:instance clip
                      :type :audio-clip}
             :playing? true
             :current-file file-path)))

  (stop [this]
    (when-let [clip (:instance (:player @app-state))]
      (.stop clip)
      (.close clip)
      (swap! app-state assoc :playing? false :player nil)))

  (cleanup [this]
    (stop this)))

;; Factory function to get the right player
(defn get-player-for-file [file-path]
  (let [extension (str/lower-case (last (str/split file-path #"\.")))]
    (case extension
      "mp3" (->MP3Player)
      ("flac" "ogg" "wav") (->AudioClipPlayer)
      nil)))

;; Play a media file
(defn play-media [file-path]
  (when-let [current-player (:player @app-state)]
    (stop (case (:type current-player)
            :mp3 (->MP3Player)
            :audio-clip (->AudioClipPlayer)
            nil)))

  (when-let [player (get-player-for-file file-path)]
    (play-file player file-path)))

;; UI Components and Functions
(defn create-ui []
  (let [;; Main components
        frame (s/frame :title "Clojure Media Player"
                       :size [500 :by 400]
                       :on-close :exit)

        ;; Playlist components
        playlist-model (javax.swing.DefaultListModel.)
        playlist-list (s/listbox :model playlist-model
                                 :selection-mode :single)
        playlist-scroll (s/scrollable playlist-list)

        ;; Control components
        now-playing (s/label :text "No file playing"
                             :font (f/font :size 14))

        play-button (s/button :text "Play"
                              :enabled? false)

        stop-button (s/button :text "Stop"
                              :enabled? false)

        add-file-button (s/button :text "Add File")

        control-panel (s/vertical-panel
                       :items [play-button stop-button add-file-button]
                       :border 5)

        ;; Main layout
        main-panel (s/border-panel
                    :north now-playing
                    :center playlist-scroll
                    :west control-panel
                    :border 10)]

    ;; Add the main panel to the frame
    (s/config! frame :content main-panel)

    ;; Event handlers
    (s/listen add-file-button :action
              (fn [e]
                (when-let [file (chooser/choose-file
                                 :type :open
                                 :filters [["Audio Files" ["mp3" "wav" "flac" "ogg"]]])]
                  (let [file-path (.getAbsolutePath file)
                        file-name (.getName file)]
                    (swap! app-state update :playlist conj file-path)
                    (.addElement playlist-model file-name)
                    (s/config! play-button :enabled? true)))))

    (s/listen play-button :action
              (fn [e]
                (let [selected-index (s/selection playlist-list)]
                  (when selected-index
                    (let [file-path (nth (:playlist @app-state) selected-index)
                          file-name (last (str/split file-path #"[/\\]"))]
                      (play-media file-path)
                      (s/config! now-playing :text (str "Now playing: " file-name))
                      (s/config! stop-button :enabled? true))))))

    (s/listen stop-button :action
              (fn [e]
                (when-let [current-player (:player @app-state)]
                  (stop (case (:type current-player)
                          :mp3 (->MP3Player)
                          :audio-clip (->AudioClipPlayer)
                          nil)))
                (s/config! now-playing :text "Playback stopped")
                (s/config! stop-button :enabled? false)))

;; Replace the problematic binding code with:
    (add-watch app-state :ui-update
               (fn [_ _ old-state new-state]
    ;; Update stop button based on playing state
                 (s/config! stop-button :enabled? (:playing? new-state))

    ;; Update play button - enabled when not playing and playlist has items
                 (s/config! play-button :enabled?
                            (and (not (:playing? new-state))
                                 (seq (:playlist new-state))))))

;; Call the watcher once to set initial state
    (swap! app-state identity)


    ;; Return the frame
    (s/pack! frame)
    (s/show! frame)
    frame))

;; Main entry point
(defn -main [& args]
  (s/invoke-later
   (create-ui)))
