set -eu

GITHUB_BRANCH=${GITHUB_REF#refs/heads/}

if [ "$GITHUB_REPOSITORY" == "google/volley" ] && \
   [ "$GITHUB_EVENT_NAME" == "push" ] && \
   [ "$GITHUB_BRANCH" == "master" ]; then
  echo -e "Publishing snapshot build...\n"

  ./gradlew publish

  echo -e "Published snapshot build"
else
  echo -e "Not publishing snapshot"
fi
